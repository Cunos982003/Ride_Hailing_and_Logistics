package com.ridehailing.common.dto;

public record ServiceStatus(String service, String status) {
  public static ServiceStatus up(String service) {
    return new ServiceStatus(service, "UP");
  }
}
