package com.ridehailing.gateway.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class SessionManager {
  private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

  private final Map<String, WsSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> userToSession = new ConcurrentHashMap<>();

  public void addSession(WebSocketSession session, String endpoint) {
    WsSession wsSession = new WsSession(session, endpoint);
    sessions.put(session.getId(), wsSession);
    log.info("Session added: {} on endpoint {}", session.getId(), endpoint);
  }

  public void removeSession(String sessionId) {
    WsSession wsSession = sessions.remove(sessionId);
    if (wsSession != null && wsSession.getUserId() != null) {
      userToSession.remove(wsSession.getUserId());
      log.info("Session removed: {} for user ", sessionId, wsSession.getUserId());
    } else {
      log.info("Session removed: {}", sessionId);
    }
  }

  public WsSession getSession(String sessionId) {
    return sessions.get(sessionId);
  }

  public WsSession getSessionByUserId(String userId) {
    String sessionId = userToSession.get(userId);
    return sessionId != null ? sessions.get(sessionId) : null;
  }

  public void authenticateSession(String sessionId, String userId, String role) {
    WsSession wsSession = sessions.get(sessionId);
    if (wsSession != null) {
      wsSession.setUserId(userId);
      wsSession.setRole(role);
      wsSession.setAuthenticated(true);
      userToSession.put(userId, sessionId);
      log.info("Session authenticated: {} for user {} with role {}", sessionId, userId, role);
    }
  }

  public Map<String, WsSession> getAllSessions() {
    return sessions;
  }
}
