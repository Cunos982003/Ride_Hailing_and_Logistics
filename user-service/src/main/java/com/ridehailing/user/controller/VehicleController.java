package com.ridehailing.user.controller;

import com.ridehailing.user.dto.RegisterVehicleRequest;
import com.ridehailing.user.dto.VehicleResponse;
import com.ridehailing.user.service.VehicleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {
  private final VehicleService vehicleService;

  public VehicleController(VehicleService vehicleService) {
    this.vehicleService = vehicleService;
  }

  @PostMapping("/users/{userId}")
  @ResponseStatus(HttpStatus.CREATED)
  public VehicleResponse registerVehicle(
      @PathVariable UUID userId, @Valid @RequestBody RegisterVehicleRequest request) {
    return vehicleService.registerVehicle(userId, request);
  }

  @GetMapping("/users/{userId}")
  public List<VehicleResponse> getDriverVehicles(@PathVariable UUID userId) {
    return vehicleService.getDriverVehicles(userId);
  }
}
