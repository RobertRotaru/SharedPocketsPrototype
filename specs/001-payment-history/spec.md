# Feature Specification: Group Payment History

**Feature Branch**: `001-payment-history`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "I want to add a new functionality, to see the payment history within a group. Any transaction in this history should include Description, Person paying, Datetime as public data and ID and Idempotency-Key as private data. If anything is missing from the data layer, it should be added and also provide backwards compatibility. To get the history, a new endpoint must be made. We can also include a caching mechanism for retrieving the history."

## Clarifications

### Session 2026-11-11

- Q: How many transactions per page, and in what order? → A: 10 transactions per page, newest first.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View a group's payment history (Priority: P1)

A group member wants to see every payment recorded in their group, so they can understand how the
current balances came about. They request the history for a group and receive the list of
transactions, each showing what it was for, who paid, and when it happened.

**Why this priority**: This is the core value of the feature. Balances alone tell members what is
owed but not why; the history makes the numbers explainable and auditable.

**Independent Test**: Record two expenses and one settlement in a group, request its history, and
verify all three appear with the correct description, payer and date and time, newest first,
without any private data visible.

**Acceptance Scenarios**:

1. **Given** a group with three recorded transactions, **When** the history is requested, **Then**
   all three are returned, each with a description, the paying member and the date and time it was
   recorded.
2. **Given** a group with no transactions, **When** the history is requested, **Then** an empty
   history is returned (not an error).
3. **Given** a group with recorded transactions, **When** the history is requested, **Then** no
   transaction exposes its internal ID or its Idempotency-Key.
4. **Given** an unknown group, **When** the history is requested, **Then** the request is rejected
   as "group not found", consistent with other group operations.
5. **Given** two groups with transactions, **When** one group's history is requested, **Then** only
   that group's transactions are returned.
6. **Given** a group with 25 transactions, **When** the first page is requested, **Then** the 10
   newest transactions are returned, newest first; the second page returns the next 10, and the
   third page returns the remaining 5 (the oldest).
7. **Given** a group with exactly 10 transactions, **When** the history is requested, **Then** one
   full page is returned and there is no further page.

---

### User Story 2 - Describe a payment when recording it (Priority: P2)

When a member records an expense or a settlement, they can add a short description (for example
"Dinner at Cais do Sodré") so the history is meaningful to everyone in the group.

**Why this priority**: Without a place to capture descriptions, the history would show only default
entries. It depends on Story 1 to be visible but is needed for the history to be useful.

**Independent Test**: Record an expense with a description, then view the history and verify the
exact description is shown.

**Acceptance Scenarios**:

1. **Given** a group member, **When** they record an expense with a description, **Then** the
   description appears on that transaction in the history.
2. **Given** a group member, **When** they record a settlement with a description, **Then** the
   description appears on that transaction in the history.
3. **Given** a description that is empty, whitespace-only or too long, **When** the payment is
   submitted, **Then** it is rejected as invalid input and no transaction or balance change is
   recorded.

---

### User Story 3 - Existing clients keep working (Priority: P2)

Clients that already record expenses and settlements without a description must continue to work
unchanged. Their transactions still appear in the history with a sensible default description.

**Why this priority**: Backwards compatibility is an explicit requirement; breaking current
clients is not acceptable for a money-moving API.

**Independent Test**: Submit an expense using exactly the request format that exists today (no
description), verify it is accepted with the same result as before, and verify it appears in the
history with the default description.

**Acceptance Scenarios**:

1. **Given** a request in the existing format without a description, **When** an expense or
   settlement is submitted, **Then** it is accepted exactly as before and balances are updated as
   before.
2. **Given** a transaction recorded without a description, **When** the history is requested,
   **Then** it appears with a clear default description for its type.
3. **Given** the existing endpoints, **When** they are used after this feature ships, **Then** their
   request formats, response formats and status codes are unchanged.

---

### User Story 4 - Fast, always-correct repeated reads (Priority: P3)

Members often reopen the history repeatedly. Repeated requests for an unchanged group should be
answered quickly, and the history must never show stale data once a new transaction has been
recorded.

**Why this priority**: A performance improvement the user described as optional ("we can also
include"). Correctness takes priority over speed.

**Independent Test**: Request the history twice in a row and verify identical results; record a new
transaction and verify the very next request includes it.

**Acceptance Scenarios**:

1. **Given** an unchanged group, **When** the history is requested repeatedly, **Then** every
   response is identical.
2. **Given** a previously requested history, **When** a new transaction is recorded in that group,
   **Then** the next history request includes the new transaction.
3. **Given** a history previously requested for group A, **When** a transaction is recorded in group
   B, **Then** group A's history is unaffected.

---

### Edge Cases

- **Idempotent replay**: Resubmitting the same Idempotency-Key with the same request adds no second
  history entry and does not change the original entry's date and time.
- **Idempotency conflict**: Reusing a key with a different request is rejected as a conflict and
  adds no history entry.
- **Failed validation**: A rejected request (invalid amount, unknown member, same-member
  settlement, invalid description) adds no history entry and does not consume its Idempotency-Key.
- **Concurrent writes**: Transactions recorded simultaneously in one group all appear in the
  history exactly once, with no loss or duplication, in a consistent order.
- **Read during write**: A history request made while transactions are being recorded returns a
  consistent snapshot, never a partial entry.
- **Ordering**: Transactions recorded at the same instant keep a stable, deterministic order
  (later-recorded first, consistent with newest first).
- **Large history**: A group with a very large number of transactions is fully reachable by paging
  through it, with correct ordering and no entry skipped or repeated across pages.
- **Last page**: The final page may hold fewer than 10 transactions; a group with no transactions
  has an empty first page.
- **Page beyond the end**: Requesting a page past the last one returns an empty page, not an error.
- **Invalid page**: A page that is not a positive whole number (zero, negative, non-numeric) is
  rejected as invalid input.
- **New transaction between pages**: Recording a transaction while a member is paging shifts
  entries by one; each individual page is still a consistent snapshot.
- **Description handling**: Descriptions with special characters, accented letters and emoji are
  returned exactly as submitted; leading and trailing whitespace is trimmed.
- **Missing vs. invalid description**: An omitted description is replaced by the default
  description; a description that is present but empty or whitespace-only is rejected as invalid.
- **Default versus explicit description on replay**: A key first used without a description and
  replayed with the literal default text (or the reverse) counts as different details and is
  rejected as a conflict; an explicitly null description counts as omitted.
- **Conflict takes precedence**: Reusing a key with different details is a conflict even when the
  new request would also be invalid on its own, as before this feature.
- **Private data exposure**: The internal ID and the Idempotency-Key never appear in any history
  response or error message.
- **Unknown group**: Requesting the history of a nonexistent group is rejected as "group not found".
- **Stale history after failure**: A rejected write must not change what the history returns.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST record every successfully applied expense and settlement as a transaction
  belonging to its group.
- **FR-002**: Each transaction MUST store a public description, the paying member, and the date and
  time it was recorded.
- **FR-003**: Each transaction MUST also store, as private data, a unique transaction ID and the
  Idempotency-Key it was submitted with.
- **FR-004**: System MUST provide a way to retrieve the history of a single group, listing its
  transactions.
- **FR-005**: The history MUST expose only the public data (description, paying member, date and
  time) and MUST NOT expose the transaction ID or the Idempotency-Key.
- **FR-006**: The history MUST be ordered by the date and time recorded, newest first, with a
  deterministic order for transactions recorded at the same instant (later-recorded first).
- **FR-006a**: The history MUST be paginated with exactly 10 transactions per page; every page
  except the last MUST be full, and the page size is fixed (not client-adjustable).
- **FR-006b**: A request MUST be able to select a page; when none is selected the first page (the
  10 newest transactions) is returned.
- **FR-006c**: Each page response MUST indicate which page it is and whether further pages exist.
- **FR-006d**: A requested page beyond the last MUST return an empty page; a page that is not a
  positive whole number MUST be rejected as invalid input.
- **FR-007**: A group with no transactions MUST return an empty first page, not an error.
- **FR-008**: A request for the history of an unknown group MUST be rejected as "group not found".
- **FR-009**: System MUST allow an optional description when recording an expense and when
  recording a settlement. An explicitly null description MUST be treated exactly like an omitted
  one.
- **FR-010**: A provided description that is empty, whitespace-only or longer than 200 characters
  MUST be rejected as invalid input, with no state change.
- **FR-011**: When no description is provided, System MUST assign a default description based on
  the transaction type (for example "Expense" or "Settlement").
- **FR-012**: Existing expense and settlement requests, responses and status codes MUST remain
  unchanged; requests without a description MUST behave exactly as before.
- **FR-013**: Replaying a request with the same Idempotency-Key and same details MUST NOT create an
  additional history entry or alter the original one.
- **FR-014**: A request reusing an Idempotency-Key with different details MUST be rejected as a
  conflict and MUST NOT create a history entry.
- **FR-015**: Rejected requests MUST NOT create a history entry or consume their Idempotency-Key.
- **FR-016**: History MUST stay consistent with balances: every applied balance change has exactly
  one corresponding history entry.
- **FR-017**: Concurrent recording and reading MUST never lose, duplicate or partially show a
  history entry.
- **FR-018**: A history request MUST always reflect every transaction recorded before it, whether
  or not an earlier response for the same page was reused. Reusing an earlier response for an
  unchanged group is permitted as an optimization and MUST NOT change what members see.
- **FR-019**: Recording a transaction in one group MUST NOT affect another group's history.
- **FR-020**: Money amounts and balance calculations MUST remain unchanged by this feature
  (exact 2-decimal amounts, balances summing to zero).

### Key Entities *(include if feature involves data)*

- **Transaction**: A single recorded expense or settlement within a group. Public attributes:
  description, paying member, date and time recorded. Private attributes: unique ID, the
  Idempotency-Key it was submitted with. Also carries its type (expense or settlement) and amount,
  which already exist in the system today.
- **Group**: Owns an ordered collection of its transactions; the history is a view of that
  collection.
- **Member**: The person identified as the payer of a transaction. For an expense, the member who
  paid; for a settlement, the member who sent the money.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of applied expenses and settlements appear in the group history exactly once.
- **SC-002**: 0 history responses contain a transaction ID or an Idempotency-Key.
- **SC-003**: 100% of requests that worked before this feature, in their existing format, still
  succeed with unchanged responses.
- **SC-004**: A member sees a newly recorded transaction in the history on their very next request
  (0 stale responses).
- **SC-005**: With 1,000 transactions recorded concurrently from 10 parallel clients, the history
  contains exactly 1,000 entries with no loss or duplicates.
- **SC-006**: Retrieving any page of a group with 10,000 transactions completes in under 1 second
  for the member.
- **SC-008**: Paging through a group of N transactions yields exactly N distinct entries across
  ceil(N/10) pages, newest first, with none skipped or repeated when no writes occur meanwhile.
- **SC-007**: Every edge case listed above is covered by at least one automated test.

## Assumptions

- "Payment history" covers both expenses and settlements, since both move money between members.
- "Person paying" is the member who paid the expense, or the member who sent the money in a
  settlement.
- "Public" data is what the history response returns to members; "private" data is stored but never
  returned by the history response.
- The date and time is the moment the transaction was applied, set by the system (not the client),
  and is returned in a standard, unambiguous format including timezone.
- Descriptions are optional, plain text, with a maximum of 200 characters.
- Storage is in-memory and cleared on restart, so no migration of previously stored data is
  required; backwards compatibility applies to the request and response formats of existing
  endpoints.
- Filtering and searching the history are out of scope for this version.
- No authentication exists, so any caller who knows a group's ID can view its history, consistent
  with the other endpoints.
- Storage remains in-memory only, as required by the project constitution.
- Caching is a transparent performance optimization and does not change what members see.
