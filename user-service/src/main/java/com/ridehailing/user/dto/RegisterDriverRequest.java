package com.ridehailing.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RegisterDriverRequest(
    @NotBlank(message = "License number is required") String licenseNumber,
    @NotNull(message = "License expiry date is required") LocalDate licenseExpiryDate) {}
