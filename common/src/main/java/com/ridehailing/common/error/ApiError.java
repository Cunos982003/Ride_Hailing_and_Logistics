package com.ridehailing.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
    Instant timestamp,
    ErrorCode code,
    String message,
    String path,
    String requestId,
    List<Violation> details) {
  public ApiError {
    details = List.copyOf(details);
  }

  public record Violation(String field, String message) {}
}
