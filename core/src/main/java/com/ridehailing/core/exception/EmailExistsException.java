package com.ridehailing.core.exception;

public class EmailExistsException extends RuntimeException {
  public EmailExistsException(String message) {
    super(message);
  }
}
