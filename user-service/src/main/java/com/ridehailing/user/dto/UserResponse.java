package com.ridehailing.user.dto;

import com.ridehailing.user.domain.Role;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String phoneNumber,
    String fullName,
    String email,
    Role role,
    Instant createdAt,
    Instant updatedAt) {}
