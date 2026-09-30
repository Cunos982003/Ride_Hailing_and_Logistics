package com.ridehailing.dispatch;

import com.ridehailing.common.dto.ServiceStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StatusController {
  @GetMapping("/api/v1/trips/status")
  ServiceStatus status() {
    return ServiceStatus.up("dispatch-service");
  }
}
