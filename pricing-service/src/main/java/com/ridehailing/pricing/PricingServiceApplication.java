package com.ridehailing.pricing;

import com.ridehailing.common.error.ApiExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ApiExceptionHandler.class)
public class PricingServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(PricingServiceApplication.class, args);
  }
}
