package com.ridehailing.location.dto;

import java.util.UUID;

public record LocationUpdate(UUID driverId, double latitude, double longitude) {
  public boolean isValidCoordinate() {
    return driverId != null
        && latitude >= -90.0
        && latitude <= 90.0
        && longitude >= -180.0
        && longitude <= 180.0;
  }
}
