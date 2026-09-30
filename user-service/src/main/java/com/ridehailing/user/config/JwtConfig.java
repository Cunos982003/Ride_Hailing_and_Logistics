package com.ridehailing.user.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {
  private final JwtProperties jwtProperties;

  public JwtConfig(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
  }

  @PostConstruct
  public void initJwt() {
    jwtProperties.init();
  }
}
