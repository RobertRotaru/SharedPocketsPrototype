# Implementation Plan: Group Payment History

**Branch**: `001-payment-history` | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-payment-history/spec.md`

## Summary

Add a paginated, newest-first payment history to each group. Every applied expense and settlement
is recorded as an immutable `Transaction` inside the `Group` aggregate (under the existing per-group
lock). A new read endpoint `GET /groups/{id}/transactions?page=N` returns 10 entries per page and
exposes only description, payer and timestamp; the transaction ID and Idempotency-Key are stored but
never serialized. Existing endpoints stay backward compatible: `description` is an optional new
field on the expense and settlement request bodies, and the existing `Group` method signatures keep
working. Repeated reads are served from a small per-group page cache that is cleared on every write
under the same lock, so it can never be stale. No new dependencies.

## Technical Context

**Language/Version**: Java 21 (Gradle toolchain)

**Primary Dependencies**: Spring Boot 3.5.6 (`spring-boot-starter-web`), springdoc-openapi 2.10.0.
No new dependencies (cache is hand-rolled, see research.md R4).

**Storage**: In-memory only (constitution). History lives inside `Group`; lost on restart.

**Testing**: JUnit 5 via `spring-boot-starter-test` (`./gradlew test`): domain unit tests,
HTTP tests in the style of the existing `GroupControllerTest`, a concurrency test.

**Target Platform**: JVM service (Windows/Linux), single instance.

**Project Type**: web-service (single Gradle project).

**Performance Goals**: any page of a 10,000-transaction group in under 1 second (SC-006); a page is
O(10) to build, a cache hit is O(1).

**Constraints**: balances still sum to 0; amounts exact to 2 decimals; per-group serialization of
writes and consistent reads; domain code free of Spring types; backward-compatible request and
response formats.

**Scale/Scope**: prototype; one new endpoint, a few new domain classes, two optional DTO fields.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Pre-research | Post-design |
|-----------|------|--------------|-------------|
| I. Money Correctness | No change to money math; amount rules untouched (FR-020) | PASS | PASS: apply logic keeps the current arithmetic verbatim |
| II. Edge Cases Before Implementation | Spec enumerates edge cases | PASS | PASS: each mapped to a test in the Test Plan below |
| III. Test-First | Tests precede code; domain testable without Spring | PASS | PASS: `TransactionHistory` is a plain class; tasks put tests first |
| IV. Idempotent Writes | Replay = no-op, conflict = 409, failed validation keeps key | PASS | PASS: history entry appended only after a successful apply, together with the key record |
| V. Concurrency Safety | Per-group lock; consistent snapshots | PASS | PASS: history and cache are mutated and read only inside `Group`'s synchronized methods |
| VI. Explicit API Contract | `{"error": ...}`, documented codes, DTOs in own files, OpenAPI, README | PASS | PASS: see [contracts/transactions-api.md](contracts/transactions-api.md) |
| Constraints: no DB, Java 21, new deps justified, repository returns `Optional` | | PASS | PASS: no new deps, no DB |

No violations; Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-payment-history/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── transactions-api.md
├── checklists/requirements.md
└── tasks.md             # created later by /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/org/example/
├── controllers/
│   ├── GroupController.java             # + GET /{id}/transactions, pass description, type-mismatch handler
│   └── dtos/
│       ├── ExpenseRequest.java          # + optional description (2-arg constructor kept)
│       ├── SettlementRequest.java       # + optional description (3-arg constructor kept)
│       ├── TransactionResponse.java     # NEW public view: description, payerId, recordedAt
│       └── TransactionPageResponse.java # NEW: page, pageSize, hasNext, items
├── domain/
│   ├── Group.java                       # records transactions, delegates to history, injectable Clock
│   ├── Transaction.java                 # NEW immutable record (public + private fields)
│   ├── TransactionType.java             # NEW enum EXPENSE, SETTLEMENT
│   ├── TransactionHistory.java          # NEW append-only list + bounded page cache (not thread-safe alone)
│   └── TransactionPage.java             # NEW domain page result (items, page, hasNext)
└── repository/                          # unchanged

src/test/java/org/example/
├── domain/
│   ├── GroupTest.java                   # + history, idempotency and description cases
│   ├── TransactionHistoryTest.java      # NEW paging, ordering, cache invalidation
│   └── GroupConcurrencyTest.java        # NEW 1,000 writes from 10 threads
└── controllers/GroupControllerTest.java # + endpoint, backward-compat and private-data tests
```

**Structure Decision**: keep the existing single-project layered layout (controllers / domain /
repository). The history belongs to the `Group` aggregate so it shares the existing lock and cannot
drift from balances; the repository interface is unchanged.

## Test Plan (edge case to test, per constitution II and III)

| Spec edge case / requirement | Test level |
|------------------------------|------------|
| Entry per applied expense and settlement (FR-001, FR-016, SC-001) | domain |
| Newest first, same-instant tie order via fixed `Clock` (FR-006) | domain |
| 25 entries page as 10/10/5; exactly 10 has no next; empty group (FR-006a-c, FR-007) | domain + HTTP |
| Page 0, negative, non-numeric rejected; page past end empty (FR-006d) | HTTP (+ domain for 0/negative) |
| Description: default, trimmed, empty/blank rejected, over 200 rejected, unicode kept (FR-009-011) | domain + HTTP |
| Replay adds no second entry and keeps timestamp; changed details 409 with no entry (FR-013, FR-014) | domain |
| Failed validation adds no entry and keeps the key usable (FR-015) | domain |
| ID and key never in JSON, even in error bodies (FR-005, SC-002) | HTTP |
| Existing request bodies and responses unchanged (FR-012, SC-003) | HTTP |
| Cache: repeat read identical; write invalidates; other groups unaffected; rejected write leaves cache valid (FR-018, FR-019) | domain |
| 1,000 concurrent writes from 10 threads give exactly 1,000 distinct entries (FR-017, SC-005, SC-008) | concurrency |
| 10,000 entries: any page under 1 s (SC-006) | domain |
| Unknown group 404 (FR-008) | HTTP |

## Complexity Tracking

No constitution violations to justify.
