package com.ridehailing.user.controller;

import com.ridehailing.user.dto.DriverResponse;
import com.ridehailing.user.dto.RegisterDriverRequest;
import com.ridehailing.user.dto.UpdateDriverStatusRequest;
import com.ridehailing.user.service.DriverService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {
  private final DriverService driverService;

  public DriverController(DriverService driverService) {
    this.driverService = driverService;
  }

  @PostMapping("/users/{userId}")
  @ResponseStatus(HttpStatus.CREATED)
  public DriverResponse registerDriver(
      @PathVariable UUID userId, @Valid @RequestBody RegisterDriverRequest request) {
    return driverService.registerDriver(userId, request);
  }

  @PatchMapping("/users/{userId}/status")
  public DriverResponse updateStatus(
      @PathVariable UUID userId, @Valid @RequestBody UpdateDriverStatusRequest request) {
    return driverService.updateStatus(userId, request);
  }

  @GetMapping("/users/{userId}")
  public DriverResponse getDriverProfile(@PathVariable UUID userId) {
    return driverService.getDriverProfile(userId);
  }
}
