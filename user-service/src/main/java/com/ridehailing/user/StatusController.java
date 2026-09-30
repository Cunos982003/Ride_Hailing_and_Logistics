package com.ridehailing.user;

import com.ridehailing.common.dto.ServiceStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StatusController {
  @GetMapping("/api/v1/users/status")
  ServiceStatus status() {
    return ServiceStatus.up("user-service");
  }
}
