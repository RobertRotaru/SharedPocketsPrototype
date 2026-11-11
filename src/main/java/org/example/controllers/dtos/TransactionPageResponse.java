package org.example.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "TransactionPageResponse", description = "One page of a group's payment history, newest first")
public record TransactionPageResponse(
        @Schema(description = "1-based page number", example = "1") int page,
        @Schema(description = "Maximum number of items per page (fixed)", example = "10") int pageSize,
        @Schema(description = "True when an older page exists") boolean hasNext,
        @Schema(description = "Transactions on this page, newest first") List<TransactionResponse> items) {}
