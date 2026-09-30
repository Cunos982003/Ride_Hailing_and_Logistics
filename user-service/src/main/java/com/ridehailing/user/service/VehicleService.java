package com.ridehailing.user.service;

import com.ridehailing.common.error.ApiException;
import com.ridehailing.common.error.ErrorCode;
import com.ridehailing.user.domain.Driver;
import com.ridehailing.user.domain.Role;
import com.ridehailing.user.domain.User;
import com.ridehailing.user.domain.Vehicle;
import com.ridehailing.user.dto.RegisterVehicleRequest;
import com.ridehailing.user.dto.VehicleResponse;
import com.ridehailing.user.repository.DriverRepository;
import com.ridehailing.user.repository.UserRepository;
import com.ridehailing.user.repository.VehicleRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {
  private final VehicleRepository vehicleRepository;
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;

  public VehicleService(
      VehicleRepository vehicleRepository,
      DriverRepository driverRepository,
      UserRepository userRepository) {
    this.vehicleRepository = vehicleRepository;
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public VehicleResponse registerVehicle(UUID userId, RegisterVehicleRequest request) {
    User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    if (!Role.DRIVER.equals(user.getRole())) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    Driver driver =
        driverRepository
            .findByUserId(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    if (vehicleRepository.existsByLicensePlate(request.licensePlate())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    Vehicle vehicle = new Vehicle();
    vehicle.setDriver(driver);
    vehicle.setVehicleType(request.vehicleType());
    vehicle.setLicensePlate(request.licensePlate());
    vehicle.setBrand(request.brand());
    vehicle.setModel(request.model());
    vehicle.setYear(request.year());
    vehicle.setColor(request.color());
    vehicle.setIsActive(true);
    vehicle.setCreatedAt(Instant.now());
    vehicle.setUpdatedAt(Instant.now());
    vehicle = vehicleRepository.save(vehicle);
    return toResponse(vehicle);
  }

  @Transactional(readOnly = true)
  public List<VehicleResponse> getDriverVehicles(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    Driver driver =
        driverRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    return vehicleRepository.findByDriverId(driver.getId()).stream().map(this::toResponse).toList();
  }

  private VehicleResponse toResponse(Vehicle vehicle) {
    return new VehicleResponse(
        vehicle.getId(),
        vehicle.getDriver().getId(),
        vehicle.getVehicleType(),
        vehicle.getLicensePlate(),
        vehicle.getBrand(),
        vehicle.getModel(),
        vehicle.getYear(),
        vehicle.getColor(),
        vehicle.getIsActive(),
        vehicle.getCreatedAt(),
        vehicle.getUpdatedAt());
  }
}
