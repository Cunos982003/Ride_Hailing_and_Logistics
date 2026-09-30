package com.ridehailing.user.dto;

import com.ridehailing.user.domain.DriverStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DriverResponse(
    UUID id,
    UUID userId,
    String licenseNumber,
    LocalDate licenseExpiryDate,
    DriverStatus driverStatus,
    BigDecimal rating,
    Integer totalTrips,
    Instant createdAt,
    Instant updatedAt) {}
