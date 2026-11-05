package org.example.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GroupResponse", description = "Response body for group information")
public record GroupResponse(String id, String name, String description) {}
