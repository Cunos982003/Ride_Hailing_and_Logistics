package com.ridehailing.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

@Component
public class WebSocketMessageBroker implements MessageListener {
  private static final Logger log = LoggerFactory.getLogger(WebSocketMessageBroker.class);
  private static final String WS_OUT_PATTERN = "ws:out:*";

  private final SessionManager sessionManager;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public WebSocketMessageBroker(
      SessionManager sessionManager,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      RedisMessageListenerContainer listenerContainer) {
    this.sessionManager = sessionManager;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;

    listenerContainer.addMessageListener(this, new PatternTopic(WS_OUT_PATTERN));
    log.info("WebSocketMessageBroker subscribed to {}", WS_OUT_PATTERN);
  }

  public void sendToUser(String userId, String json) {
    String channel = "ws:out:" + userId;
    redisTemplate.convertAndSend(channel, json);
    log.debug("Published message to channel {}", channel);
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      String channel = new String(message.getChannel());
      String userId = channel.substring("ws:out:".length());
      String json = new String(message.getBody());

      WsSession wsSession = sessionManager.getSessionByUserId(userId);
      if (wsSession != null && wsSession.getSession().isOpen()) {
        wsSession.getSession().sendMessage(new TextMessage(json));
        log.debug("Sent message to user {} via WebSocket", userId);
      } else {
        log.debug("No active session for user {}, message dropped", userId);
      }
    } catch (IOException e) {
      log.error("Failed to send WebSocket message", e);
    }
  }
}
