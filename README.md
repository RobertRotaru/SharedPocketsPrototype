# Shared Pockets Prototype

A small Java service for shared group money. A group of people (flatmates, friends on a trip) record expenses, the cost is split equally, and anyone can see who owes whom. Every money write is idempotent, so a retried request never applies twice.

Inspired by the idea of shared money in a banking app. It's an independent prototype and has no affiliation with any bank.

## Run it

Needs Java 21. The Gradle wrapper does the rest.

```bash
./gradlew bootRun
```

On Windows use `gradlew.bat bootRun`. The service listens on `http://localhost:8080`.

Run the tests (domain unit tests, a concurrency test and one HTTP test per endpoint):

```bash
./gradlew test
```

## Demo

Start the service in one terminal, then use either option below.

### Option 1: curl (Git Bash, macOS, Linux)

```bash
# 1. Create a group and keep its id
GID=$(curl -s -X POST localhost:8080/groups -H 'Content-Type: application/json' \
  -d '{"name":"Lisbon trip","description":"Flights, food, fun"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')

# 2. Add three members
for u in ana bob cris; do
  curl -s -X POST localhost:8080/groups/$GID/members -H 'Content-Type: application/json' \
    -d "{\"uid\":\"$u\",\"name\":\"$u\",\"email\":\"$u@example.com\"}"
done

# 3. Ana pays 100.00 for dinner
curl -s -X POST localhost:8080/groups/$GID/expenses \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: dinner-1' \
  -d '{"memberId":"ana","amount":100.00}'

# 4. Who owes what? Expect {"ana":66.66,"bob":-33.33,"cris":-33.33}
curl -s localhost:8080/groups/$GID/balances

# 5. Retry the same request: nothing changes (idempotent)
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/groups/$GID/expenses \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: dinner-1' \
  -d '{"memberId":"ana","amount":100.00}'
curl -s localhost:8080/groups/$GID/balances

# 6. Reuse the key with a different amount: 409 Conflict
curl -s -w '\n%{http_code}\n' -X POST localhost:8080/groups/$GID/expenses \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: dinner-1' \
  -d '{"memberId":"ana","amount":999.00}'

# 7. Bob pays Ana 33.33, so Bob is back to 0
curl -s -X POST localhost:8080/groups/$GID/settlements \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: settle-1' \
  -d '{"payerId":"bob","receiverId":"ana","amount":33.33}'
curl -s localhost:8080/groups/$GID/balances
```

The balances always sum to exactly 0. Positive means the group owes that member, negative means that member owes the group.

### Option 2: IntelliJ

Open [demo.http](demo.http) and press the green arrow next to each request, top to bottom. The group id is captured automatically.

## API

| Method | Path | Body | Success |
| --- | --- | --- | --- |
| `POST` | `/groups` | `{"name","description"}` | 201 with `{"id","name","description"}` |
| `POST` | `/groups/{id}/members` | `{"uid","name","email"}` | 201 |
| `POST` | `/groups/{id}/expenses` | `{"memberId","amount"}` and an `Idempotency-Key` header | 201 |
| `GET` | `/groups/{id}/balances` | none | 200 with `{"<uid>": <balance>}` in join order |
| `POST` | `/groups/{id}/settlements` | `{"payerId","receiverId","amount"}` and an `Idempotency-Key` header | 201 |

Errors come back as `{"error": "..."}`:

| Status | When |
| --- | --- |
| 400 | invalid input: amount zero, negative or with more than 2 decimals, unknown member, missing field or `Idempotency-Key` header |
| 404 | unknown group |
| 409 | duplicate member, or an `Idempotency-Key` reused for a different request |

## Design: four guarantees

1. **Exact money.** Amounts are `BigDecimal` with at most 2 decimals. The share is rounded down, and any leftover cents go one each to members in join order, so 100.00 between three people is 33.34 / 33.33 / 33.33.
2. **Balances sum to 0.** The payer is credited the full amount and every member, the payer included, is debited their share. Settlements move the same amount between two members.
3. **Idempotent writes.** Each group remembers every `Idempotency-Key` with a fingerprint of the request. The same key and details is a no-op, and the same key with different details is a 409. A request that fails validation does not use up its key.
4. **No lost updates.** Each group has its own lock, so writes to one group are serialized and different groups don't block each other. Reads take the same lock, so a balances response is a consistent snapshot. A concurrency test fires 1,000 expenses from 10 threads and asserts the exact balances.

## Trade-offs

- In-memory storage: everything is lost on restart, and it runs on a single instance.
- Equal splits only, one currency, no users or authentication.
- Idempotency keys are scoped to a group and kept forever.
- A settlement can exceed what is owed. It's allowed, and the balances show the overpayment.

## What I'd do next

- Postgres with a double-entry ledger and a unique constraint on the idempotency key.
- An outbox to Kafka for notifications.
- Sharding by group id.
- Custom splits and currencies.
- Idempotency key expiry.
- An "ask your pocket" AI query over a group's history.
