package org.example.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One applied expense or settlement in a group's history.
 *
 * <p>Public data (shown in the history): {@code description}, {@code payerId}, {@code recordedAt}.
 * Private data (stored, never serialized): {@code id} and {@code idempotencyKey}.
 *
 * @param id             system-generated UUID, unique per transaction (private)
 * @param idempotencyKey key the write was submitted with; null for legacy keyless calls (private)
 * @param type           expense or settlement
 * @param payerId        expense: the member who paid; settlement: the member who sends the money
 * @param receiverId     settlements only; null for expenses
 * @param amount         positive, scale at most 2
 * @param description    trimmed, 1 to 200 characters; defaults to "Expense" or "Settlement"
 * @param recordedAt     set by the server clock when the write is applied (UTC)
 */
public record Transaction(
        String id,
        String idempotencyKey,
        TransactionType type,
        String payerId,
        String receiverId,
        BigDecimal amount,
        String description,
        Instant recordedAt) {
}
