# Quickstart: Validating Payment History

Prerequisites: Java 21; run from the repository root.

## 1. Automated validation

```bash
./gradlew test
```

Expected: all tests pass, including the history, backward-compatibility, private-data and
concurrency tests listed in the plan's Test Plan.

## 2. Manual end-to-end walk-through

```bash
./gradlew bootRun    # Windows: gradlew.bat bootRun
```

In another terminal (Git Bash):

```bash
GID=$(curl -s -X POST localhost:8080/groups -H 'Content-Type: application/json' \
  -d '{"name":"Lisbon trip","description":"Flights, food, fun"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
for u in ana bob cris; do
  curl -s -X POST localhost:8080/groups/$GID/members -H 'Content-Type: application/json' \
    -d "{\"uid\":\"$u\",\"name\":\"$u\",\"email\":\"$u@example.com\"}"
done

# Empty history
curl -s localhost:8080/groups/$GID/transactions
# expect: {"page":1,"pageSize":10,"hasNext":false,"items":[]}

# With a description, and an old-style body without one
curl -s -X POST localhost:8080/groups/$GID/expenses -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: e1' -d '{"memberId":"ana","amount":100.00,"description":"Dinner"}'
curl -s -X POST localhost:8080/groups/$GID/expenses -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: e2' -d '{"memberId":"bob","amount":30.00}'
curl -s -X POST localhost:8080/groups/$GID/settlements -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s1' -d '{"payerId":"cris","receiverId":"ana","amount":33.33}'

curl -s localhost:8080/groups/$GID/transactions
# expect 3 items, newest first: Settlement (cris), Expense (bob), Dinner (ana);
# no "id" or idempotency key anywhere; recordedAt in ISO-8601 UTC
```

## 3. Scenario checks

| Scenario | Command | Expected |
|----------|---------|----------|
| Replay | repeat the `e1` request | 201, history unchanged |
| Key reuse, different description | `e1` with `"description":"Lunch"` | 409, history unchanged |
| Blank description | expense with `"description":"   "` | 400, history unchanged, key reusable |
| Too long | description of 201 characters | 400 |
| Page past end | `GET .../transactions?page=99` | 200, empty `items`, `hasNext` false |
| Bad page | `?page=0`, `?page=-1`, `?page=abc` | 400 with `{"error": ...}` |
| Unknown group | `GET /groups/nope/transactions` | 404 |
| Paging | record 25 expenses, then `?page=1`, `2`, `3` | 10, 10, 5 items; `hasNext` true, true, false |
| Cache freshness | read page 1 twice, add an expense, read again | first two identical; third shows the new entry first |
| Balances | `GET .../balances` | unchanged behavior; values sum to 0 |

Details: [contracts/transactions-api.md](contracts/transactions-api.md),
[data-model.md](data-model.md).
