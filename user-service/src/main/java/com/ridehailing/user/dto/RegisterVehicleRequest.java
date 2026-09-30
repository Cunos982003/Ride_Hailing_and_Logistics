package com.ridehailing.user.dto;

import com.ridehailing.user.domain.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterVehicleRequest(
    @NotNull(message = "Vehicle type is required") VehicleType vehicleType,
    @NotBlank(message = "License plate is required") @Size(max = 20) String licensePlate,
    @NotBlank(message = "Brand is required") @Size(max = 100) String brand,
    @NotBlank(message = "Model is required") @Size(max = 100) String model,
    @NotNull(message = "Year is required") @Min(1990) @Max(2100) Integer year,
    @NotBlank(message = "Color is required") @Size(max = 50) String color) {}
