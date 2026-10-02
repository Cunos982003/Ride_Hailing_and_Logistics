package com.ridehailing.core.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

  @PostMapping
  public ResponseEntity<?> createRide(
      @RequestBody Map<String, Object> request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal String userId) {

    // TODO: Implement ride creation logic with dispatch service
    // For now, return mock response to test web app

    return ResponseEntity.ok(
        Map.of(
            "id", "temp-ride-123",
            "status", "PENDING",
            "pickup_lat", request.get("pickup_lat"),
            "pickup_lng", request.get("pickup_lng"),
            "dropoff_lat", request.get("dropoff_lat"),
            "dropoff_lng", request.get("dropoff_lng"),
            "customer_id", userId,
            "created_at", System.currentTimeMillis()));
  }
}
