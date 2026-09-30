package com.ridehailing.user.dto;

import com.ridehailing.user.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^\\+?[0-9]{10,15}$",
            message = "Phone number must be 10-15 digits, optional + prefix")
        String phoneNumber,
    @NotBlank(message = "Password is required") @Size(min = 8, message = "Password must be at least 8 characters")
        String password,
    @NotBlank(message = "Full name is required") @Size(max = 255) String fullName,
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format")
        String email,
    @NotNull(message = "Role is required") Role role) {}
