package com.ridehailing.gateway.websocket;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.socket.WebSocketSession;

public class WsSession {
  private final WebSocketSession session;
  private final String endpoint;
  private String userId;
  private String role;
  private boolean authenticated;
  private Instant lastMessageTime;
  private Instant lastPongTime;
  private final AtomicInteger messageCount;
  private Instant windowStart;

  public WsSession(WebSocketSession session, String endpoint) {
    this.session = session;
    this.endpoint = endpoint;
    this.authenticated = false;
    this.lastMessageTime = Instant.now();
    this.lastPongTime = Instant.now();
    this.messageCount = new AtomicInteger(0);
    this.windowStart = Instant.now();
  }

  public WebSocketSession getSession() {
    return session;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public boolean isAuthenticated() {
    return authenticated;
  }

  public void setAuthenticated(boolean authenticated) {
    this.authenticated = authenticated;
  }

  public Instant getLastMessageTime() {
    return lastMessageTime;
  }

  public void updateLastMessageTime() {
    this.lastMessageTime = Instant.now();
  }

  public Instant getLastPongTime() {
    return lastPongTime;
  }

  public void updateLastPongTime() {
    this.lastPongTime = Instant.now();
  }

  // Rate limiting: 5 messages per second
  public boolean checkRateLimit() {
    Instant now = Instant.now();
    long secondsSinceWindowStart = now.getEpochSecond() - windowStart.getEpochSecond();

    if (secondsSinceWindowStart >= 1) {
      // Reset window
      windowStart = now;
      messageCount.set(1);
      return true;
    }

    int count = messageCount.incrementAndGet();
    return count <= 5;
  }

  public String getSessionId() {
    return session.getId();
  }
}
