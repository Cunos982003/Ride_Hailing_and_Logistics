package com.ridehailing.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Requested representation is not available"),
  CONFLICT(HttpStatus.CONFLICT, "Resource conflict"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
