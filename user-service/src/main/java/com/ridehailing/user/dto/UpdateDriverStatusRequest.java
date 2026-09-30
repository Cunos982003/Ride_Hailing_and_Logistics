package com.ridehailing.user.dto;

import com.ridehailing.user.domain.DriverStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDriverStatusRequest(@NotNull(message = "Status is required") DriverStatus status) {}
