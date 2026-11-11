# SharedPockets Constitution

SharedPockets is a prototype: a small Spring Boot service with no database that registers members
into groups, records equal-split expenses and settlements, and reports balances. It exists to
demonstrate fintech-grade engineering discipline, so correctness and rigor outrank feature breadth.

## Core Principles

### I. Money Correctness (NON-NEGOTIABLE)
All monetary values MUST be `BigDecimal` with a maximum of 2 decimal places; `float` and `double`
MUST NOT represent money anywhere. Rounding MUST be explicit and deterministic (share rounded down,
leftover cents distributed one each in member join order). For every group, balances MUST sum to
exactly 0 after every operation. Amounts that are zero, negative, or carry more than 2 decimals MUST
be rejected.
Rationale: in financial software a single lost cent is a defect, not a rounding detail.

### II. Edge Cases Before Implementation
Every feature spec MUST enumerate its edge cases (invalid input, boundaries, duplicates, unknown
entities, retries, concurrency) and the expected behavior for each BEFORE any implementation is
planned. A plan or task list that does not cover every enumerated edge case MUST NOT proceed to
implementation.
Rationale: edge cases found after coding are the ones that reach production.

### III. Test-First with Unit Tests (NON-NEGOTIABLE)
Tests MUST be written before the code they verify and MUST fail first (Red-Green-Refactor). Every
edge case from Principle II MUST map to at least one automated unit test. Domain logic MUST be
unit-testable without Spring context, HTTP, or I/O. Each endpoint MUST have an HTTP-level test, and
shared-state behavior MUST have a concurrency test with exact-value assertions. `./gradlew test`
MUST pass before any change is considered complete.
Rationale: this repository's purpose is to prove disciplined, verifiable engineering.

### IV. Idempotent Writes
Every money-moving write (expenses, settlements) MUST require an `Idempotency-Key`. Replaying the
same key with the same request MUST be a no-op; the same key with a different request MUST return
409 Conflict. A request that fails validation MUST NOT consume its key.
Rationale: clients retry; a retry must never move money twice.

### V. Concurrency Safety & Consistency
Writes to a single group MUST be serialized, and reads MUST observe a consistent snapshot. Different
groups MUST NOT block each other. No operation may produce a lost update or a partially applied
state; an operation either fully applies or has no effect.
Rationale: correct balances under concurrent load is a baseline expectation for payment systems.

### VI. Explicit, Predictable API Contract
Errors MUST be returned as `{"error": "..."}` with the documented status codes (400 invalid input,
404 unknown group, 409 conflict). Behavior MUST be defined, not accidental: validation lives in the
domain, controllers stay thin, and request/response DTOs live in their own files. The API MUST be
documented via OpenAPI/Swagger and the README MUST match actual behavior, including known trade-offs.
Rationale: a clear contract is auditable and lets reviewers verify behavior without reading code.

## Technical Constraints

- Stack: Java 21, Spring Boot, Gradle. New dependencies MUST be justified by a concrete need.
- Storage: in-memory only; no database. Persistence MUST NOT be added in this prototype, and the
  trade-off (state lost on restart, single instance) MUST remain documented in the README.
- Scope: equal splits, a single currency, no authentication. Anything beyond this scope belongs in
  the README "What I'd do next" list unless this constitution is amended.
- Domain code MUST NOT depend on Spring or web types; repositories MUST return `Optional` for
  lookups that can miss.

## Development Workflow & Quality Gates

1. Specify: write the spec including the edge-case catalogue (Principle II).
2. Plan and tasks: tests appear before implementation tasks (Principle III).
3. Implement: Red-Green-Refactor in small, reviewable commits.
4. Gate: `./gradlew test` MUST pass; no test may be skipped or deleted to make a change pass.
5. Review: every change MUST be checked against this constitution. Violations MUST be fixed or
   explicitly justified in writing in the plan's complexity/justification section.
6. Docs: update the README and OpenAPI annotations whenever behavior changes.

## Governance

This constitution supersedes other practices in this repository. Amendments require a written
rationale, an updated version number, and a review of existing specs, plans, and tests for
compliance. Versioning follows semantic versioning: MAJOR for removed or redefined principles,
MINOR for added principles or materially expanded guidance, PATCH for clarifications. Compliance
MUST be reviewed on every plan and every change; unjustified complexity or principle violations
block merge.

**Version**: 1.0.0 | **Ratified**: 2026-11-11 | **Last Amended**: 2026-11-11
