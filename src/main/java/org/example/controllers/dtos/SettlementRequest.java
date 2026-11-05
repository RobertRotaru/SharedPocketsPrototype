package org.example.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "SettlementRequest", description = "Request body for settling an amount between two members")
public record SettlementRequest(String payerId, String receiverId, BigDecimal amount) {}

