# Shared Pockets Prototype

Nov 1, 2026 · @Robert Rotaru

A small Java service for shared group money (expenses, balances, settle-up), built in **4 hours** to show interest in Revolut and the skills their interviews test.

## What and why

**The idea.** Families, flatmates and friends on a trip share costs. Shared Pockets keeps one pot per group: members add expenses, the cost is split, and anyone can see who owes whom.

**Why it fits Revolut:**

- Shared money is a gap in the Revolut app today, and it extends my own budgeting app (shared family budgets).
- It shows exactly what their engineers test: exact money handling, idempotent requests, thread safety and TDD.
- It reuses the expense-splitter kata, so the core logic already exists.

**Kept simple on purpose:**

| In scope | Out of scope |
| --- | --- |
| Java 21, plain Java domain plus a thin HTTP layer (Spring Boot, web only) | Database, Docker, Kubernetes |
| In-memory storage behind a repository interface | Auth, users, UI |
| Equal split, balances, settle-up | Custom splits, currencies |
| Idempotency key on every write | Kafka, events, AI |
| JUnit 5 tests, including one concurrency test | Load tests, CI pipeline |

The out-of-scope column becomes the "what I'd do next" section of the README, which is just as valuable in an interview.

**Endpoints (5):**

| Method | Path | Does |
| --- | --- | --- |
| `POST` | `/groups` | Create a group |
| `POST` | `/groups/{id}/members` | Add a member |
| `POST` | `/groups/{id}/expenses` | Record an expense (`Idempotency-Key` header) |
| `GET` | `/groups/{id}/balances` | Every member's balance |
| `POST` | `/groups/{id}/settlements` | Record a payment between two members (`Idempotency-Key` header) |

**Rules the code guarantees:**

1. Money is `BigDecimal` at 2 decimals; leftover cents go to members in join order.
2. **Balances in a group always sum to exactly 0.**
3. The same `Idempotency-Key` never applies twice; reused with different details, it's rejected.
4. Concurrent requests never lose an expense (one lock per group).

## The 4-hour plan

| Step | Time | Output |
| --- | --- | --- |
| 1. Set up | 20 min | Gradle project, Java 21, JUnit 5, Spring Boot; public GitHub repo |
| 2. Domain, test first | 75 min | `Group` class: add member, add expense, balances, settle-up |
| 3. Idempotency | 30 min | Same key applies once; different details rejected |
| 4. Concurrency test | 25 min | 1,000 concurrent expenses, exact balances asserted |
| 5. HTTP layer | 40 min | The 5 endpoints, JSON in and out, errors as 400 / 404 / 409 |
| 6. README | 30 min | Design notes, how to run, what's next |
| Buffer | 20 min | Fixes, cleanup, push |

Copy the splitting logic from the expense-splitter kata (`ExpenseSplitter` and its tests) in step 2. That saves most of the domain time.

## Tests to write (in this order)

- [ ] Duplicate member rejected
- [ ] Expense by a non-member rejected
- [ ] Invalid amounts rejected (one `@ParameterizedTest`: 0, −5)
- [ ] Two members: 100 → +50 / −50
- [ ] Three members: 90 → +60 / −30 / −30
- [ ] Three members: 100 → leftover cent, balances sum to 0
- [ ] Settle-up: after Bob pays Ana 50, both are at 0
- [ ] Same idempotency key twice → applied once
- [ ] Same key, different amount → rejected
- [ ] 1,000 concurrent expenses → exact final balances
- [ ] One HTTP test per endpoint: happy path only

After each green: break the code for 5 seconds and check the test goes red.

## README outline

1. **What it is**, in two sentences.
2. **How to run it:** one command, plus three `curl` examples.
3. **Design:** the four guaranteed rules above, and how each is enforced (a lock per group, a key map, `BigDecimal` rounding).
4. **Trade-offs:** in-memory, single instance, equal splits only.
5. **What I'd do next:** Postgres with a double-entry ledger and a unique idempotency key; an outbox to Kafka for notifications; sharding by group ID; custom splits and currencies; an "ask your pocket" AI query.

## How to present it

- **In the interview:** when asked "why Revolut?" or "what would you improve?", say: "Shared money is a gap I noticed, so I built a small prototype. The interesting part was keeping balances exact under concurrency and retries." Then give the GitHub link.
- **Optionally**, a one-line note to the recruiter with the link, asking for nothing.
- **Pin it** on GitHub and LinkedIn.
- Describe it as **inspired by** Revolut, never as a Revolut product: no Revolut name or logo in the app itself.
