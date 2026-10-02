package com.ridehailing.core.config;

import com.ridehailing.core.filter.InternalKeyFilter;
import com.ridehailing.core.filter.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final InternalKeyFilter internalKeyFilter;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter, InternalKeyFilter internalKeyFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.internalKeyFilter = internalKeyFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/auth/**", "/health", "/api/v1/health")
                    .permitAll()
                    .requestMatchers(request -> "OPTIONS".equals(request.getMethod()))
                    .permitAll()
                    .requestMatchers("/api/v1/rides/**")
                    .hasRole("CUSTOMER")
                    .requestMatchers("/api/v1/driver/**", "/api/v1/drivers/**")
                    .hasRole("DRIVER")
                    .requestMatchers("/internal/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) -> {
                          response.setStatus(401);
                          response.setContentType("application/json");
                          response
                              .getWriter()
                              .write(
                                  "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          response.setStatus(403);
                          response.setContentType("application/json");
                          response
                              .getWriter()
                              .write("{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                        }))
        .addFilterBefore(internalKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:8001", "http://localhost:8002"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
