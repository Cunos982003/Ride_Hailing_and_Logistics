package com.ridehailing.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.common.jwt.JwtUtil;
import com.ridehailing.gateway.websocket.LocationBatchProcessor;
import com.ridehailing.gateway.websocket.RideWebSocketHandler;
import com.ridehailing.gateway.websocket.SessionManager;
import com.ridehailing.gateway.websocket.WebSocketMessageBroker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

  private final SessionManager sessionManager;
  private final JwtUtil jwtUtil;
  private final ObjectMapper objectMapper;
  private final LocationBatchProcessor locationBatchProcessor;
  private final WebSocketMessageBroker messageBroker;
  private final StringRedisTemplate redisTemplate;

  public WebSocketConfig(
      SessionManager sessionManager,
      JwtUtil jwtUtil,
      ObjectMapper objectMapper,
      LocationBatchProcessor locationBatchProcessor,
      WebSocketMessageBroker messageBroker,
      StringRedisTemplate redisTemplate) {
    this.sessionManager = sessionManager;
    this.jwtUtil = jwtUtil;
    this.objectMapper = objectMapper;
    this.locationBatchProcessor = locationBatchProcessor;
    this.messageBroker = messageBroker;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(driverWebSocketHandler(), "/ws/driver")
        .setAllowedOrigins("*")
        .addInterceptors(new ConnectionTimeInterceptor());

    registry
        .addHandler(customerWebSocketHandler(), "/ws/customer")
        .setAllowedOrigins("*")
        .addInterceptors(new ConnectionTimeInterceptor());
  }

  @Bean
  public WebSocketHandler driverWebSocketHandler() {
    return new RideWebSocketHandler(
        sessionManager,
        jwtUtil,
        objectMapper,
        locationBatchProcessor,
        messageBroker,
        redisTemplate,
        "/ws/driver",
        "DRIVER");
  }

  @Bean
  public WebSocketHandler customerWebSocketHandler() {
    return new RideWebSocketHandler(
        sessionManager,
        jwtUtil,
        objectMapper,
        locationBatchProcessor,
        messageBroker,
        redisTemplate,
        "/ws/customer",
        "CUSTOMER");
  }

  private static class ConnectionTimeInterceptor extends HttpSessionHandshakeInterceptor {
    @Override
    public boolean beforeHandshake(
        org.springframework.http.server.ServerHttpRequest request,
        org.springframework.http.server.ServerHttpResponse response,
        org.springframework.web.socket.WebSocketHandler wsHandler,
        java.util.Map<String, Object> attributes)
        throws Exception {
      attributes.put("connectionTime", System.currentTimeMillis());
      return super.beforeHandshake(request, response, wsHandler, attributes);
    }
  }
}
