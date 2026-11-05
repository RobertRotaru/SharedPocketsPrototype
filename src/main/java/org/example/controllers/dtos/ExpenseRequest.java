package org.example.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "ExpenseRequest", description = "Request body for adding an expense to a member")
public record ExpenseRequest(String memberId, BigDecimal amount) {}

