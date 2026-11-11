package org.example.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "TransactionResponse", description = "Public view of one payment in a group's history. "
        + "The internal transaction id and the Idempotency-Key are never exposed.")
public record TransactionResponse(
        @Schema(description = "What the payment was for", example = "Dinner") String description,
        @Schema(description = "Member who paid (for a settlement, the member who sent the money)", example = "ana") String payerId,
        @Schema(description = "When the payment was recorded (ISO-8601, UTC)", example = "2026-11-11T18:42:07.123Z") Instant recordedAt) {}
