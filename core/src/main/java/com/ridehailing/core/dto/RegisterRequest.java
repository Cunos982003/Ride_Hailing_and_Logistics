package com.ridehailing.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,
    @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,
    @NotBlank(message = "Full name is required") String fullName,
    @NotBlank(message = "Role is required")
        @Pattern(regexp = "CUSTOMER|DRIVER", message = "Role must be CUSTOMER or DRIVER")
        String role) {}
