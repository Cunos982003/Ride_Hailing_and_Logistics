package com.ridehailing.dispatch;

import com.ridehailing.common.error.ApiExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ApiExceptionHandler.class)
public class DispatchServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(DispatchServiceApplication.class, args);
  }
}
