package com.ridehailing.user.service;

import com.ridehailing.common.error.ApiException;
import com.ridehailing.common.error.ErrorCode;
import com.ridehailing.user.domain.Driver;
import com.ridehailing.user.domain.DriverStatus;
import com.ridehailing.user.domain.Role;
import com.ridehailing.user.domain.User;
import com.ridehailing.user.dto.DriverResponse;
import com.ridehailing.user.dto.RegisterDriverRequest;
import com.ridehailing.user.dto.UpdateDriverStatusRequest;
import com.ridehailing.user.repository.DriverRepository;
import com.ridehailing.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {
  private final DriverRepository driverRepository;
  private final UserRepository userRepository;

  public DriverService(DriverRepository driverRepository, UserRepository userRepository) {
    this.driverRepository = driverRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public DriverResponse registerDriver(UUID userId, RegisterDriverRequest request) {
    User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    if (!Role.DRIVER.equals(user.getRole())) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    if (driverRepository.findByUserId(userId).isPresent()) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (driverRepository.existsByLicenseNumber(request.licenseNumber())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    Driver driver = new Driver();
    driver.setUser(user);
    driver.setLicenseNumber(request.licenseNumber());
    driver.setLicenseExpiryDate(request.licenseExpiryDate());
    driver.setDriverStatus(DriverStatus.OFFLINE);
    driver.setCreatedAt(Instant.now());
    driver.setUpdatedAt(Instant.now());
    driver = driverRepository.save(driver);
    return toResponse(driver);
  }

  @Transactional
  public DriverResponse updateStatus(UUID userId, UpdateDriverStatusRequest request) {
    User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    Driver driver =
        driverRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    driver.setDriverStatus(request.status());
    driver = driverRepository.save(driver);
    return toResponse(driver);
  }

  @Transactional(readOnly = true)
  public DriverResponse getDriverProfile(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    Driver driver =
        driverRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    return toResponse(driver);
  }

  private DriverResponse toResponse(Driver driver) {
    return new DriverResponse(
        driver.getId(),
        driver.getUser().getId(),
        driver.getLicenseNumber(),
        driver.getLicenseExpiryDate(),
        driver.getDriverStatus(),
        driver.getRating(),
        driver.getTotalTrips(),
        driver.getCreatedAt(),
        driver.getUpdatedAt());
  }
}
