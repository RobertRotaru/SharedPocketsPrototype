# Research: Group Payment History

No `NEEDS CLARIFICATION` items remained in the spec; this records the technical decisions the spec
deliberately left open.

## R1. Where the history lives

- **Decision**: An append-only list of immutable `Transaction` records held by the `Group`
  aggregate (wrapped in a plain `TransactionHistory` class), mutated only inside `Group`'s
  synchronized methods.
- **Rationale**: The group already serializes all reads and writes with one lock, so a history entry
  is appended atomically with the balance change and idempotency record. It cannot disagree with
  balances (FR-016) and needs no new locking.
- **Alternatives**: A separate history repository or service (adds a second lock and a window where
  balances and history disagree); a concurrent queue (does not give atomicity with balances).

## R2. Ordering and pagination

- **Decision**: Order is insertion order reversed (newest first). Page `n` (1-based) of size 10 is
  the slice of the list taken from the end. The page is chosen with `?page=N`, default 1. The
  response carries `page`, `pageSize`, `hasNext` and `items`.
- **Rationale**: The insertion sequence is the authoritative tie-break (FR-006) and is immune to
  clock skew. Slicing is O(page size). Page-number paging matches the "10 per page" request and the
  spec assumption.
- **Alternatives**: Sort by timestamp (tie-break and clock-adjustment risk); cursor/keyset paging
  (stable under concurrent inserts, but the spec accepts page shifting and page numbers are simpler;
  a possible follow-up); client-adjustable page size (explicitly not wanted).

## R3. Backward compatibility of the data layer

- **Decision**:
  - Add optional `description` to `ExpenseRequest` and `SettlementRequest`. A missing property
    deserializes to `null`, so old bodies work unchanged; secondary constructors keep existing
    callers compiling.
  - Keep every public `Group` method signature. The two-argument `addExpense`/`settleUp` (no key)
    still apply and now also record an entry with no Idempotency-Key and the default description.
    The key-taking overloads gain a description-taking overload, and the old ones delegate with
    `null`.
  - The request fingerprint used for idempotency stays byte-identical when no description is
    supplied, and appends the normalized description only when one is supplied. Same key plus same
    amounts but a different description is therefore a 409 conflict ("different details").
  - The order of checks stays exactly as today (replay or conflict before member and amount
    validation); only description normalization runs first because the fingerprint needs it.
  - An explicit JSON `null` description is treated as omitted.
  - No data migration: storage is in-memory.
- **Rationale**: Satisfies FR-012 and SC-003 without versioning the API.
- **Alternatives**: A `v2` endpoint (needless duplication); mandatory description (breaks clients);
  excluding description from the fingerprint (a replay with a different description would silently
  succeed and the stored description would not match what the client sent).

## R4. Caching

- **Decision**: A per-group access-ordered map of page number to immutable `TransactionPage`,
  capped at 100 pages inside `TransactionHistory`. It is cleared on every append. Because the
  history and the cache are touched only under the group lock, a hit is always identical to a
  recomputation.
- **Rationale**: Honors the "caching mechanism" wish with zero new dependencies (constitution:
  dependencies must be justified) and zero staleness risk, because invalidation is part of the write
  path itself. A rejected write never reaches the append, so the cache is untouched. Honest note: a
  page is already O(10) to build, so the cache is a demonstrable optimization more than a necessity;
  it is isolated in one class and can be removed without changing behavior.
- **Alternatives**: Spring Cache with Caffeine (new dependencies; invalidation outside the group
  lock risks races); HTTP `ETag`/`Cache-Control` (stale-read risk for a mutable resource); no cache
  (simplest, but the user asked for one).

## R5. Timestamps

- **Decision**: `java.time.Instant` from an injectable `java.time.Clock` (default
  `Clock.systemUTC()`) supplied to `Group`; the existing 3-argument constructor keeps using the
  system clock. Serialized as ISO-8601 UTC (Spring Boot's default Jackson config writes `Instant`
  as a string).
- **Rationale**: Server-set time (spec assumption), unambiguous timezone, and a fixed clock makes
  ordering and timestamp tests deterministic.
- **Alternatives**: `LocalDateTime` (no zone); client-supplied time (untrusted).

## R6. Description rules and error handling

- **Decision**: `null` means "not provided" and becomes `Expense` or `Settlement`. A provided value
  is trimmed; empty or blank after trimming, or longer than 200 characters, throws
  `IllegalArgumentException` (HTTP 400 via the existing handler) before any state change, so the
  Idempotency-Key is not consumed. A non-numeric `page` raises Spring's
  `MethodArgumentTypeMismatchException`, mapped to 400 `{"error": ...}` by a new handler in the
  controller; `page < 1` is rejected by the domain. A page past the end returns an empty page.
- **Rationale**: Matches FR-009 to FR-011 and FR-006d and keeps validation in the domain.
- **Alternatives**: Bean Validation annotations (new dependency and rules outside the domain).

## R7. What the response exposes

- **Decision**: `TransactionResponse` contains exactly `description`, `payerId` and `recordedAt`;
  it is built from `Transaction` by an explicit mapper, so the ID and Idempotency-Key cannot leak by
  serializing the domain object. `Transaction` is never returned directly. For a settlement the
  payer is the member who sends the money.
- **Rationale**: FR-005 and SC-002 by construction, plus a test asserting the JSON field set.
- **Note**: Per the spec, amount and type are stored but not exposed. Adding them later is an
  additive, backward-compatible change to the response.
