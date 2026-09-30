package com.ridehailing.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Object> handleApiException(
      ApiException exception, HttpServletRequest request) {
    return error(
        exception.code(), exception.code().status(), new HttpHeaders(), request, List.of());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ApiError.Violation> details =
        exception.getBindingResult().getFieldErrors().stream()
            .map(field -> new ApiError.Violation(field.getField(), field.getDefaultMessage()))
            .sorted(java.util.Comparator.comparing(ApiError.Violation::field))
            .toList();
    return error(ErrorCode.VALIDATION_ERROR, status, headers, servletRequest(request), details);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ErrorCode code =
        switch (status.value()) {
          case 400 -> ErrorCode.MALFORMED_REQUEST;
          case 401 -> ErrorCode.UNAUTHORIZED;
          case 403 -> ErrorCode.FORBIDDEN;
          case 404 -> ErrorCode.NOT_FOUND;
          case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
          case 406 -> ErrorCode.NOT_ACCEPTABLE;
          case 409 -> ErrorCode.CONFLICT;
          case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
          default -> ErrorCode.INTERNAL_ERROR;
        };
    return error(code, status, headers, servletRequest(request), List.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleUnexpected(Exception exception, HttpServletRequest request) {
    LOG.error("Unhandled request failure at {}", request.getRequestURI(), exception);
    return error(
        ErrorCode.INTERNAL_ERROR,
        ErrorCode.INTERNAL_ERROR.status(),
        new HttpHeaders(),
        request,
        List.of());
  }

  private HttpServletRequest servletRequest(WebRequest request) {
    return ((ServletWebRequest) request).getRequest();
  }

  private ResponseEntity<Object> error(
      ErrorCode code,
      HttpStatusCode status,
      HttpHeaders originalHeaders,
      HttpServletRequest request,
      List<ApiError.Violation> details) {
    String suppliedId = request.getHeader("X-Request-Id");
    String requestId =
        suppliedId != null && suppliedId.matches("[A-Za-z0-9_-]{1,64}")
            ? suppliedId
            : UUID.randomUUID().toString();
    HttpHeaders headers = new HttpHeaders();
    headers.putAll(originalHeaders);
    headers.set("X-Request-Id", requestId);
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    ApiError body =
        new ApiError(
            Instant.now(), code, code.message(), request.getRequestURI(), requestId, details);
    return new ResponseEntity<>(body, headers, status);
  }
}
