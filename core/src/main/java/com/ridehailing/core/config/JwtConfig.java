package com.ridehailing.core.config;

import com.ridehailing.common.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

  @Bean
  public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret) {
    return new JwtUtil(secret);
  }
}
