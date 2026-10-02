package com.ridehailing.gateway.websocket;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class HeartbeatScheduler {
  private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);
  private static final long PONG_TIMEOUT_SECONDS = 60;

  private final SessionManager sessionManager;

  public HeartbeatScheduler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Scheduled(fixedDelay = 25_000)
  public void sendPings() {
    sessionManager
        .getAllSessions()
        .values()
        .forEach(
            wsSession -> {
              WebSocketSession session = wsSession.getSession();
              if (!session.isOpen()) {
                sessionManager.removeSession(session.getId());
                return;
              }

              // Check if pong timeout exceeded
              Duration timeSinceLastPong =
                  Duration.between(wsSession.getLastPongTime(), Instant.now());
              if (timeSinceLastPong.getSeconds() > PONG_TIMEOUT_SECONDS) {
                log.warn(
                    "Session {} timed out - no pong for {} seconds",
                    session.getId(),
                    timeSinceLastPong.getSeconds());
                try {
                  session.close(new CloseStatus(1000, "Pong timeout"));
                } catch (IOException e) {
                  log.error("Failed to close timed-out session {}", session.getId(), e);
                }
                sessionManager.removeSession(session.getId());
                return;
              }

              // Send ping
              try {
                session.sendMessage(new PingMessage());
                log.debug("Sent ping to session {}", session.getId());
              } catch (IOException e) {
                log.error("Failed to send ping to session {}", session.getId(), e);
                sessionManager.removeSession(session.getId());
              }
            });
  }
}
