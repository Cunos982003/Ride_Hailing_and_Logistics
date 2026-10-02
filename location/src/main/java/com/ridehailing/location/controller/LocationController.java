package com.ridehailing.location.controller;

import com.ridehailing.location.dto.Candidate;
import com.ridehailing.location.dto.LocationUpdate;
import com.ridehailing.location.service.LocationService;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class LocationController {

  private final LocationService locationService;
  private final String internalKey;

  public LocationController(
      LocationService locationService,
      @Value("${internal.key:${app.internal-key:secret}}") String internalKey) {
    this.locationService = locationService;
    this.internalKey = internalKey;
  }

  @PostMapping("/locations")
  public ResponseEntity<Void> updateLocations(
      @RequestHeader(value = "X-Internal-Key", required = false) String key,
      @RequestBody List<LocationUpdate> updates) {
    if (key == null || !internalKey.equals(key)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    locationService.update(updates);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/drivers/nearby")
  public ResponseEntity<List<Candidate>> findNearby(
      @RequestHeader(value = "X-Internal-Key", required = false) String key,
      @RequestParam double latitude,
      @RequestParam double longitude,
      @RequestParam double radiusKm,
      @RequestParam(defaultValue = "10") int limit) {
    if (key == null || !internalKey.equals(key)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    List<Candidate> candidates = locationService.findNearby(latitude, longitude, radiusKm, limit);
    return ResponseEntity.ok(candidates);
  }

  @DeleteMapping("/drivers/{id}")
  public ResponseEntity<Void> removeDriver(
      @RequestHeader(value = "X-Internal-Key", required = false) String key,
      @PathVariable UUID id) {
    if (key == null || !internalKey.equals(key)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    locationService.removeDriver(id);
    return ResponseEntity.ok().build();
  }
}
