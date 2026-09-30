package com.ridehailing.common.event;

import com.ridehailing.common.dto.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TripCompletedEvent(
    UUID tripId, UUID passengerId, UUID driverId, Money fare, Instant completedAt) {
  public static final String TYPE = "trip.completed";

  public TripCompletedEvent {
    Objects.requireNonNull(tripId, "tripId");
    Objects.requireNonNull(passengerId, "passengerId");
    Objects.requireNonNull(driverId, "driverId");
    Objects.requireNonNull(fare, "fare");
    Objects.requireNonNull(completedAt, "completedAt");
  }
}
