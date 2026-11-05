package org.example.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateGroupRequest", description = "Request body for creating a new group")
public record CreateGroupRequest(String name, String description) {}

