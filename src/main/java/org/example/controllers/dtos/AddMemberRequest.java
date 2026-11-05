package org.example.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AddMemberRequest", description = "Request body for adding a new member to a group")
public record AddMemberRequest(String uid, String name, String email) {}

