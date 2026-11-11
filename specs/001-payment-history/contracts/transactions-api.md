# Contract: Payment History API

Base path `/groups`. All errors use `{"error": "<message>"}`.

## GET /groups/{id}/transactions

Returns one page of the group's history, newest first, 10 entries per page.

| Parameter | In | Type | Default | Rules |
|-----------|----|------|---------|-------|
| `id` | path | string | | Existing group id |
| `page` | query | integer | `1` | Positive whole number |

**200 OK**

```json
{
  "page": 1,
  "pageSize": 10,
  "hasNext": true,
  "items": [
    { "description": "Dinner", "payerId": "ana", "recordedAt": "2026-11-11T18:42:07.123Z" },
    { "description": "Settlement", "payerId": "bob", "recordedAt": "2026-11-11T18:40:55.004Z" }
  ]
}
```

- `items` holds at most 10 entries, newest first; empty for a group with no transactions or a page
  past the end (`hasNext` is `false`).
- `recordedAt` is ISO-8601 UTC.
- The response never contains a transaction id or Idempotency-Key.

| Status | When |
|--------|------|
| 200 | Page returned (possibly empty) |
| 400 | `page` is zero, negative or not a whole number |
| 404 | Unknown group |

## Changed: POST /groups/{id}/expenses

Request body gains an optional field; everything else is unchanged.

```json
{ "memberId": "ana", "amount": 100.00, "description": "Dinner" }
```

## Changed: POST /groups/{id}/settlements

```json
{ "payerId": "bob", "receiverId": "ana", "amount": 33.33, "description": "Paying back dinner" }
```

`description` rules (both endpoints): optional; if omitted the default is `Expense` or
`Settlement`; if present it is trimmed and must be 1 to 200 characters, otherwise 400. Requests
without `description`, their responses (201, no body) and status codes are exactly as before.
Reusing an `Idempotency-Key` with a different `description` is a 409, the same as for any other
changed detail.

## Compatibility matrix

| Client sends | Result |
|--------------|--------|
| Old body, no `description` | Accepted as before; entry shows the default description |
| New body with `description` | Accepted; entry shows the description |
| Same key, same body (replay) | 201 as before, no new entry |
| Same key, different body or description | 409, no new entry |
