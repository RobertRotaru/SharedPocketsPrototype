# Tasks: Group Payment History

**Input**: Design documents from `/specs/001-payment-history/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/transactions-api.md, quickstart.md

**Branch**: `001-payment-history` (all work happens on this branch)

**Tests**: REQUIRED. The project constitution (Principles II and III) mandates test-first work: every
test task below MUST be written first and MUST FAIL before its implementation task is started.
`./gradlew test` MUST pass at the end of every phase. Never skip or delete a test to make it pass.

**Organization**: Tasks are grouped by user story. Paths are relative to the repository root.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 view history (P1), US2 describe a payment (P2), US3 backward compatibility (P2),
  US4 cached reads (P3)

---

## Phase 1: Setup

- [X] T001 Confirm `git branch --show-current` prints `001-payment-history`, then run `./gradlew test` and record that the existing suite (`src/test/java/org/example/domain/GroupTest.java`, `src/test/java/org/example/controllers/GroupControllerTest.java`) is green before any change

---

## Phase 2: Foundational (blocks all user stories)

**Purpose**: the history building blocks shared by every story. Domain code MUST NOT import Spring or
web types (constitution, Technical Constraints).

- [X] T002 [P] Create enum `TransactionType` with values `EXPENSE` and `SETTLEMENT` in `src/main/java/org/example/domain/TransactionType.java`
- [X] T003 [P] Create immutable record `Transaction` in `src/main/java/org/example/domain/Transaction.java` with fields: `id` (String UUID, private, unique, never serialized), `idempotencyKey` (String, private, nullable, `null` for legacy keyless calls), `type` (`TransactionType`), `payerId` (String; expense: the member who paid, settlement: the member who sends money), `receiverId` (String, nullable, settlements only), `amount` (BigDecimal, positive, scale at most 2), `description` (String, trimmed, 1 to 200 characters), `recordedAt` (`Instant`, UTC, set by server clock, never changed on replay)
- [X] T004 [P] Create immutable record `TransactionPage` in `src/main/java/org/example/domain/TransactionPage.java` with `items` (`List<Transaction>`, at most 10, newest first, defensively copied), `page` (int), `hasNext` (boolean); a page beyond the last has empty `items` and `hasNext = false`
- [X] T005 Write failing unit tests in `src/test/java/org/example/domain/TransactionHistoryTest.java` for `TransactionHistory` (no Spring context): empty history gives an empty first page with `hasNext` false; 25 entries page as 10, 10, 5 newest first with `hasNext` true, true, false; exactly 10 entries give one full page and `hasNext` false; a page past the end is empty with `hasNext` false; page 0 and page -1 throw `IllegalArgumentException`; entries are returned in reverse order of `append` (insertion sequence is the tie-break, not the timestamp); 10,001 entries still page correctly (first, middle and last page)
- [X] T006 Implement `TransactionHistory` in `src/main/java/org/example/domain/TransactionHistory.java` (depends on T002 to T005): append-only `List<Transaction>` in order of recording with `append(Transaction)` and `page(int page)`; fixed page size constant of 10; slice taken from the end of the list; `page >= 1` else `IllegalArgumentException("Page must be a positive whole number.")`; class is NOT thread-safe by itself (documented in Javadoc: callers hold the group lock). Make T005 pass
- [X] T007 Add an injectable `java.time.Clock` to `src/main/java/org/example/domain/Group.java`: new constructor `Group(String gid, String name, String description, Clock clock)`; the existing 3-argument constructor MUST keep working and delegate with `Clock.systemUTC()`; add a `TransactionHistory` field. No behavior change yet; the existing suite from T001 MUST still pass

**Checkpoint**: building blocks exist, existing tests still green.

---

## Phase 3: User Story 1 - View a group's payment history (Priority: P1) 🎯 MVP

**Goal**: every applied expense and settlement is recorded and a paginated, newest-first history
with only public data is retrievable through a new endpoint.

**Independent Test**: record two expenses and one settlement in a group, `GET /groups/{id}/transactions`,
verify three entries, newest first, with description, payer and datetime, and no id or Idempotency-Key.

### Tests for User Story 1 (write first, they MUST fail)

- [X] T008 [P] [US1] Add failing domain tests to `src/test/java/org/example/domain/GroupTest.java` using a fixed/stepping test `Clock`: an applied expense and an applied settlement each create exactly one entry (payer = expense payer / settlement sender, `recordedAt` = clock instant); entries are newest first; two writes at the same instant keep later-recorded first; the legacy two-argument `addExpense`/`settleUp` (no key) also create one entry with a `null` Idempotency-Key; the entry `id` is non-null and unique across entries; `getTransactionPage` on a group with 25 transactions pages 10, 10, 5
- [X] T009 [P] [US1] Add failing domain tests to `src/test/java/org/example/domain/GroupTest.java` for idempotency and failure: replaying the same key with the same request adds no second entry and leaves the original `recordedAt` unchanged; reusing a key with a different request throws `ConflictException` and adds no entry; a rejected request (unknown member, zero or negative amount, more than 2 decimals, settlement with the same member twice, unknown receiver) adds no entry and does not consume its key (the same key then succeeds once corrected); balances still sum to exactly 0 after every case
- [X] T010 [P] [US1] Add failing HTTP tests to `src/test/java/org/example/controllers/GroupControllerTest.java` for `GET /groups/{id}/transactions`: empty group returns 200 with `{"page":1,"pageSize":10,"hasNext":false,"items":[]}`; after one expense and one settlement the items are newest first with exactly the fields `description`, `payerId`, `recordedAt` (assert the JSON key set, so no `id`, `idempotencyKey` or `Idempotency-Key` can appear anywhere in the body); 25 expenses page as 10, 10, 5 for `page=1,2,3` with `hasNext` true, true, false; no `page` parameter equals `page=1`; `page=99` returns 200 with empty `items`; `page=0`, `page=-1` and `page=abc` each return 400 with an `{"error": ...}` body that contains no id or key; unknown group returns 404; two groups never see each other's entries; `recordedAt` parses as an ISO-8601 UTC instant
- [X] T011 [P] [US1] Create failing concurrency test `src/test/java/org/example/domain/GroupConcurrencyTest.java`: 10 threads record 1,000 expenses in total on one group with distinct Idempotency-Keys, then assert exactly 1,000 history entries (walk all 100 pages), all `id` values distinct, no duplicates or losses, and exact balances; a second test reads pages while writers run and asserts every read returns a consistent page (at most 10 items, newest first, no partial entry)

### Implementation for User Story 1

- [X] T012 [US1] In `src/main/java/org/example/domain/Group.java` refactor the existing balance logic into private `applyExpense(...)` and `applySettlement(...)` methods that keep the current arithmetic verbatim (share rounded down, leftover cents one each in join order, same validation messages); build a `Transaction` (`UUID.randomUUID().toString()` id, key, type, payer, receiver, amount, default description `Expense` or `Settlement`, `Instant.now(clock)`) and `append` it to the history only after the balance change succeeds; the key-taking methods record the key and append together after a successful apply and still return early on a replay; the keyless methods append with a `null` key. The key-taking methods MUST call the new private `applyExpense`/`applySettlement`, NEVER the public keyless overloads (today they do, which would append two history entries per write); keep the existing order: replay or conflict check first, then validation, then apply (depends on T006, T007; makes T008, T009 pass)
- [X] T013 [US1] Add `public synchronized TransactionPage getTransactionPage(int page)` to `src/main/java/org/example/domain/Group.java` delegating to the history so reads share the group lock (makes T011 consistency test pass)
- [X] T014 [P] [US1] Create `src/main/java/org/example/controllers/dtos/TransactionResponse.java` as a record `(String description, String payerId, Instant recordedAt)` with an OpenAPI `@Schema`; it MUST NOT contain the id or Idempotency-Key
- [X] T015 [P] [US1] Create `src/main/java/org/example/controllers/dtos/TransactionPageResponse.java` as a record `(int page, int pageSize, boolean hasNext, List<TransactionResponse> items)` with an OpenAPI `@Schema`, in its own file
- [X] T016 [US1] Add `GET /groups/{id}/transactions` to `src/main/java/org/example/controllers/GroupController.java` with `@RequestParam(defaultValue = "1") int page`, `@Operation` and `@ApiResponses` for 200, 400 and 404; resolve the group with the existing `requireGroup`; map each `Transaction` to `TransactionResponse` through an explicit mapping (never serialize the domain `Transaction`); set `pageSize` to 10 (depends on T013 to T015)
- [X] T017 [US1] Add a handler in `src/main/java/org/example/controllers/GroupController.java` for `MethodArgumentTypeMismatchException` returning 400 with `{"error": "Page must be a positive whole number."}` so a non-numeric `page` uses the project error shape (makes T010 pass)
- [X] T018 [US1] Run `./gradlew test`; all US1 tests and the pre-existing suite MUST pass

**Checkpoint**: MVP. The history is viewable, paged, newest first and leak-free.

---

## Phase 4: User Story 2 - Describe a payment when recording it (Priority: P2)

**Goal**: expenses and settlements accept an optional description that appears in the history.

**Independent Test**: record an expense with `"description":"Dinner"`, view the history, see `Dinner`.

### Tests for User Story 2 (write first, they MUST fail)

- [X] T019 [P] [US2] Add failing domain tests to `src/test/java/org/example/domain/GroupTest.java`: a provided description is stored and returned exactly (accented letters, emoji and special characters preserved); leading and trailing whitespace is trimmed; a description of exactly 200 characters is accepted and 201 characters is rejected; an empty string and a whitespace-only string throw `IllegalArgumentException` for both expense and settlement; a rejected description adds no entry, changes no balance and does not consume the Idempotency-Key; the same key with the same amount but a different description is a `ConflictException` with no new entry; the same key with the same description is an idempotent replay; an explicit `null` description behaves exactly like an omitted one (default text, same fingerprint); a key first used with the description omitted and replayed with the literal default text (`Expense`) is a `ConflictException` (different details), and the reverse likewise
- [X] T020 [P] [US2] Add failing HTTP tests to `src/test/java/org/example/controllers/GroupControllerTest.java`: expense and settlement bodies with `description` return 201 and the description appears in the history; `"description":""`, `"description":"   "` and a 201-character description return 400 with `{"error": ...}` and leave history and balances unchanged; the same `Idempotency-Key` with a different description returns 409

### Implementation for User Story 2

- [X] T021 [P] [US2] Add an optional `String description` component to `src/main/java/org/example/controllers/dtos/ExpenseRequest.java` and `src/main/java/org/example/controllers/dtos/SettlementRequest.java`, updating the `@Schema` text; a missing JSON property deserializes to `null`
- [X] T022 [US2] In `src/main/java/org/example/domain/Group.java` add description-taking overloads `addExpense(String uid, BigDecimal amount, String idempotencyKey, String description)` and `settleUp(String uid1, String uid2, BigDecimal amount, String idempotencyKey, String description)` (name the parameter `idempotencyKey`, not `transactionId`, to avoid confusion with `Transaction.id`); add a private `normalizeDescription(String raw, TransactionType type)`: `null` (omitted, including an explicit JSON null) returns the default `Expense` or `Settlement`; otherwise trim, then throw `IllegalArgumentException` if the result is empty or longer than 200 characters ("Description must be 1 to 200 characters."). Order of operations: normalize the description first (it is needed for the fingerprint and a rejected description never consumes the key), then the existing replay or conflict check, then the existing member and amount validation, then apply and append; extend the fingerprint with the normalized description ONLY when one was supplied (so a request that omitted it and one that typed the default text are different details) (depends on T012; makes T019 and T026 pass)
- [X] T023 [US2] Pass `request.description()` from the expense and settlement endpoints in `src/main/java/org/example/controllers/GroupController.java` to the new overloads, and add the `description` field to the `@Operation` descriptions (makes T020 pass)
- [X] T024 [US2] Run `./gradlew test`; US1 and US2 tests and the pre-existing suite MUST pass

**Checkpoint**: descriptions are captured, validated and shown in the history.

---

## Phase 5: User Story 3 - Existing clients keep working (Priority: P2)

**Goal**: nothing that worked before changes; legacy requests show a default description.

**Independent Test**: send an expense in the exact pre-feature request format, confirm identical
status and body to before, and see `Expense` in the history.

### Tests for User Story 3 (write first)

- [X] T025 [P] [US3] Add compatibility tests to `src/test/java/org/example/controllers/GroupControllerTest.java`: pre-feature bodies `{"memberId":..,"amount":..}` and `{"payerId":..,"receiverId":..,"amount":..}` return 201 with an empty body exactly as before; balances after them are identical to the pre-feature expectations (for example `100.00` between three members gives `66.66 / -33.33 / -33.33` per the README); the history shows the default descriptions `Expense` and `Settlement`; the replay (201, no change) and conflict (409) behaviors of the README demo still hold; existing error statuses (400 invalid input, 404 unknown group, 409 duplicate member) are unchanged
- [X] T026 [P] [US3] Add compatibility tests to `src/test/java/org/example/domain/GroupTest.java`: every pre-feature public signature still compiles and behaves as before (`addExpense(uid, amount)`, `addExpense(uid, amount, key)`, `settleUp(uid1, uid2, amount)`, `settleUp(uid1, uid2, amount, key)`, the 3-argument `Group` constructor); a key first used through the old 3-argument overload and replayed through the new overload with a `null` description is an idempotent no-op, and vice versa (fingerprint is byte-identical when no description is supplied); preserved check order: reusing a key with a different AND invalid body (for example a negative amount) still gives `ConflictException` (409), not a 400, exactly as before this feature

### Implementation for User Story 3

- [X] T027 [US3] Keep backward compatibility in code: add secondary constructors `ExpenseRequest(String memberId, BigDecimal amount)` and `SettlementRequest(String payerId, String receiverId, BigDecimal amount)` that pass `null` description in `src/main/java/org/example/controllers/dtos/ExpenseRequest.java` and `src/main/java/org/example/controllers/dtos/SettlementRequest.java`; make the old key-taking `Group` overloads delegate to the new description overloads with `null` in `src/main/java/org/example/domain/Group.java`; fix any test that no longer compiles by using these constructors, never by changing assertions
- [X] T028 [US3] Run `./gradlew test`; T025 and T026 and the full suite MUST pass without modifying any pre-existing assertion

**Checkpoint**: zero breaking changes (SC-003).

---

## Phase 6: User Story 4 - Fast, always-correct repeated reads (Priority: P3)

**Goal**: repeated page reads are served from a cache that can never be stale.

**Independent Test**: read page 1 twice (identical), record a transaction, read again (new entry first).

### Tests for User Story 4 (write first, they MUST fail)

- [X] T029 [P] [US4] Add failing cache tests to `src/test/java/org/example/domain/TransactionHistoryTest.java`: repeated `page(n)` returns an equal (and, for a hit, the same immutable) `TransactionPage`; `append` invalidates the cache so the next read includes the new entry newest first; the cache holds at most 100 pages (reading 101 distinct pages evicts the least recently used and the results stay correct); a page beyond the end and page 0 are not cached as errors
- [X] T030 [P] [US4] Add failing cache tests to `src/test/java/org/example/domain/GroupTest.java`: after a rejected write (invalid amount, invalid description, key conflict) the next read equals the previous read; a write in group B never changes group A's page; a replay does not invalidate or alter the history

### Implementation for User Story 4

- [X] T031 [US4] In `src/main/java/org/example/domain/TransactionHistory.java` add an access-ordered `LinkedHashMap<Integer, TransactionPage>` page cache capped at 100 entries via `removeEldestEntry`; `page(int)` returns the cached page when present, otherwise builds, stores and returns it; `append` clears the cache; pages are immutable so they are safe to share; keep it inside the group lock (no new dependencies; research R4) (makes T029, T030 pass)
- [X] T032 [US4] Re-run `src/test/java/org/example/domain/GroupConcurrencyTest.java` from T011 and `./gradlew test`; concurrent reads and writes MUST still give exact, consistent pages

**Checkpoint**: caching adds speed with no staleness.

---

## Phase 7: Polish & Cross-Cutting

- [X] T033 [P] Add a performance test in `src/test/java/org/example/domain/TransactionHistoryTest.java`: with 10,000 entries, retrieving the first, a middle and the last page each completes in under 1 second (SC-006)
- [X] T034 [P] Update `README.md`: add the `GET /groups/{id}/transactions` row to the API table with the `page` parameter, document the optional `description` field and its rules (1 to 200 characters, default `Expense`/`Settlement`), the 400 for a bad `page`, newest first with 10 per page, that the id and Idempotency-Key are never exposed, and add the history guarantee, the page-shifting trade-off, the in-memory cache and the "cursor paging" idea to the existing Design, Trade-offs and "What I'd do next" sections (constitution Principle VI)
- [X] T035 [P] Add history requests (empty history, a described expense, page 2) to `demo.http` at the repository root
- [X] T036 Verify OpenAPI: start the service with `./gradlew bootRun` and confirm Swagger UI at `http://localhost:8080/swagger-ui.html` shows the new endpoint, the `page` parameter and the optional `description` field with the documented responses
- [X] T037 Walk through every step and scenario of `specs/001-payment-history/quickstart.md` against the running service and confirm each expected result
- [X] T038 Map each Edge Cases bullet in `specs/001-payment-history/spec.md` and each SC-001 to SC-008 to at least one passing test (SC-007), and add any missing test; then run the full `./gradlew test` one final time and confirm all tests pass

---

## Dependencies & Execution Order

- **Phase 1** then **Phase 2** (blocks everything). Within Phase 2: T002, T003, T004 in parallel; T005 before T006; T007 after T001.
- **US1** (Phase 3) after Phase 2. **US2** after US1 (it builds on `Group` recording from T012 and the DTOs). **US3** after US2 (it relies on the new overloads and DTO fields to prove compatibility). **US4** depends only on Phase 2 and US1 and may run in parallel with US2/US3 if `TransactionHistory.java` edits are not simultaneous.
- **Polish** after all desired stories.
- Inside every story: tests first (must fail), then implementation, then a green `./gradlew test`.
- Tasks that edit the same file (`Group.java`, `GroupController.java`, `GroupTest.java`, `GroupControllerTest.java`, `TransactionHistory.java`) are NOT parallel with each other even if marked [P] in different phases.

## Parallel Opportunities

- Phase 2: T002, T003, T004.
- US1 tests: T008, T009, T010, T011 (T008 and T009 touch the same file, so write them in one sitting); US1 DTOs: T014, T015.
- US2: T019, T020 together; T021 in parallel with T019/T020.
- US3: T025, T026.
- US4: T029, T030.
- Polish: T033, T034, T035.

## Implementation Strategy

1. **MVP first**: Phases 1 to 3. Stop and validate US1 against its Independent Test and the
   quickstart before continuing.
2. **Incremental**: add US2 (descriptions), then US3 (compatibility proof), then US4 (cache), running
   `./gradlew test` and committing at each checkpoint.
3. Commit per phase on branch `001-payment-history` with a conventional message; open a PR to `master`
   only after T038 is green.
4. Constitution gates: before merging confirm Principles I to VI still hold (money math untouched,
   every edge case tested, idempotency and concurrency preserved, README and OpenAPI updated).
