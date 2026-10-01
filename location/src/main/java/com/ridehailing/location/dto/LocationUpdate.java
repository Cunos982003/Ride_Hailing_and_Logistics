package com.ridehailing.location.dto;

import java.util.UUID;

public record LocationUpdate(
    UUID driverId,
    double latitude,
    double longitude
) {
  public boolean isValidCoordinate() {
    return latitude >= -90 && latitude <= 90
        && longitude >= -180 && longitude <= 180;
  }
}
