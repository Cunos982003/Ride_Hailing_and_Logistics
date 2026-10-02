package com.ridehailing.gateway.config;

import com.ridehailing.common.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GatewayConfig {

  @Bean
  public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret) {
    return new JwtUtil(secret);
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
