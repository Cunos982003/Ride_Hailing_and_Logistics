package com.ridehailing.user.dto;

import com.ridehailing.user.domain.VehicleType;
import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
    UUID id,
    UUID driverId,
    VehicleType vehicleType,
    String licensePlate,
    String brand,
    String model,
    Integer year,
    String color,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt) {}
