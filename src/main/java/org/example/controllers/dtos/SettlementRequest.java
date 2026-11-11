package org.example.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "SettlementRequest", description = "Request body for settling an amount between two members")
public record SettlementRequest(
        String payerId,
        String receiverId,
        BigDecimal amount,
        @Schema(description = "Optional text shown in the payment history (1 to 200 characters after trimming). "
                + "Defaults to \"Settlement\" when omitted.", example = "Paying back dinner") String description) {

    /** Pre-description format, kept so existing callers keep compiling. */
    public SettlementRequest(String payerId, String receiverId, BigDecimal amount) {
        this(payerId, receiverId, amount, null);
    }
}
