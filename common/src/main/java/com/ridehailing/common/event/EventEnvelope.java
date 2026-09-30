package com.ridehailing.common.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope<T>(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    String producer,
    UUID correlationId,
    T payload) {
  public EventEnvelope {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(correlationId, "correlationId");
    Objects.requireNonNull(payload, "payload");
    if (schemaVersion != 1) {
      throw new IllegalArgumentException("Unsupported event schema version");
    }
    if (eventType == null || eventType.isBlank() || producer == null || producer.isBlank()) {
      throw new IllegalArgumentException("Event type and producer are required");
    }
  }

  public static <T> EventEnvelope<T> v1(
      String type, String producer, UUID correlationId, Instant occurredAt, T payload) {
    return new EventEnvelope<>(
        UUID.randomUUID(), type, 1, occurredAt, producer, correlationId, payload);
  }
}
