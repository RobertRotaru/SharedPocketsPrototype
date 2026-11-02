# Implementation Plan

Follow the steps in order. Finish each with green tests, then break the code for 5 seconds and check the test goes red.

Based on a read of the code on Nov 1, 2026. The tests have not been run.

## Target design

- `Member` becomes `record Member(uid, name, email)`, with no debt in it.
- `Group` holds:
  - `LinkedHashMap<String, Member>` for members
  - `LinkedHashMap<String, BigDecimal>` for balances
- Plain maps preserve join order and are safe behind the group lock.
- `synchronized` is the one lock per group. It also covers reading balances, which returns a copy.
- Methods take IDs, not objects: `addExpense(key, payerId, amount)`.
- Keep running balances instead of recalculating from history, so a member who joins later doesn't share earlier expenses. The ledger goes in the README as a next step.

## Step 0: Update `CONTEXT.md` (5 min)

- [ ] Change "Idempotency key on every write" to cover the two money writes only (expenses and settlements), matching the endpoint table.
- [ ] Extend rule 1: leftover cents are added to what those members owe, and the payer is included in the join order.

## Step 1: Clean up and redesign (20 min)

**Build**

- [ ] Remove `spark-core` and Mockito from `build.gradle`.
- [ ] Set a Java 21 toolchain.
- [ ] Use the Spring Boot Gradle plugin, `spring-boot-starter-web` and `spring-boot-starter-test`. Jackson, logging and JUnit come with them.

**Domain**

- [ ] Remove `debt` from `Member` and make its constructor public.
- [ ] Replace the `ConcurrentLinkedDeque` with the two maps above. The current stream-and-remove loop may never finish, and it loses join order.
- [ ] Delete the non-split `addExpense` overload and the `isSplitInGroup` flag.
- [ ] Remove the setters and unused imports.

**Tests**

- [ ] Fix the `assertThrows` checks. The third argument is only a failure message, so use `var e = assertThrows(...)` and assert on `e.getMessage()`.
- [ ] Run the tests and confirm they're green before moving on.

## Step 2: Validation (10 min)

- [ ] Add 0 to the invalid amounts test.
- [ ] Change the error message to say "positive", not "non-negative".
- [ ] Reject amounts with more than 2 decimals.
- [ ] Normalise amounts with `setScale(2)`.

## Step 3: Split and balances (30 min)

Algorithm:

1. `share = amount / n`, rounded down to 2 decimals.
2. `leftover = amount - share * n`.
3. The first `leftover / 0.01` members in join order each pay 1 cent more.
4. The payer gets `+amount`, and every member, including the payer, gets `-share`.

Tests, one at a time:

- [ ] Two members: 100 → +50 / −50
- [ ] Three members: 90 → +60 / −30 / −30
- [ ] Three members: 100 → leftover cent, balances sum to 0
- [ ] One member: 100 → balance 0
- [ ] Add a helper that asserts the sum is 0, and use it in every balance test.

## Step 4: Settle-up (15 min)

The person paying gets `+amount` and the person receiving gets `-amount`.

- [ ] After Bob pays Ana 50, both are at 0.
- [ ] Reject non-members.
- [ ] Reject paying yourself.
- [ ] Reject invalid amounts.
- [ ] Allow overpayment, and list it as a trade-off in the README.

## Step 5: Idempotency (30 min)

- [ ] Add one map per group, from key to a fingerprint of the request type and details, plus the stored result.
- [ ] Check and apply inside the same `synchronized` method.
- [ ] Validate first, so a failed request doesn't use up the key.

Tests:

- [ ] Same key twice → applied once, stored result returned.
- [ ] Same key, different amount → conflict exception.
- [ ] Same key used for an expense and a settlement → conflict exception.

## Step 6: Concurrency (25 min)

- [ ] 1,000 tasks of 0.03 with three members, released together by a `CountDownLatch`. Expect exactly +20.00 / −10.00 / −10.00.
- [ ] 1,000 threads using the same key → applied once.
- [ ] Remove `synchronized` to confirm both tests go red, then restore it.

## Step 7: Repository and HTTP (40 min)

- [ ] Add a `GroupRepository` interface.
- [ ] Add an in-memory version on `ConcurrentHashMap`.
- [ ] Add the 5 endpoints in a Spring `@RestController`, with thin handlers. Mark the in-memory repository `@Repository` so Spring injects it.
- [ ] Take `Idempotency-Key` with `@RequestHeader`. A missing header gives 400.
- [ ] Settlement requests use `payerId` and `receiverId`. `Group.settleUp` takes the receiver first, then the payer.
- [ ] Map custom exceptions in one place:
  - 400: invalid input
  - 404: group or member not found
  - 409: duplicate member or idempotency key conflict
- [ ] Add one happy-path test per endpoint.

## Step 8: README and push (30 min)

- [ ] Follow the outline in `CONTEXT.md`.
- [ ] Explain how each of the four rules is enforced.
- [ ] Add the trade-offs and "what I'd do next".
- [ ] Push and pin the repo.
