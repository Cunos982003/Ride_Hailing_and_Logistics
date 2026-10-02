package com.ridehailing.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.common.jwt.JwtUtil;
import com.ridehailing.gateway.websocket.dto.ClientMessage;
import com.ridehailing.gateway.websocket.dto.LocationUpdate;
import com.ridehailing.gateway.websocket.dto.ServerMessage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class RideWebSocketHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(RideWebSocketHandler.class);
  private static final int MAX_MESSAGE_SIZE = 4096;
  private static final long AUTH_TIMEOUT_SECONDS = 5;

  private final SessionManager sessionManager;
  private final JwtUtil jwtUtil;
  private final ObjectMapper objectMapper;
  private final LocationBatchProcessor locationBatchProcessor;
  private final WebSocketMessageBroker messageBroker;
  private final StringRedisTemplate redisTemplate;
  private final String endpoint;
  private final String expectedRole;

  public RideWebSocketHandler(
      SessionManager sessionManager,
      JwtUtil jwtUtil,
      ObjectMapper objectMapper,
      LocationBatchProcessor locationBatchProcessor,
      WebSocketMessageBroker messageBroker,
      StringRedisTemplate redisTemplate,
      String endpoint,
      String expectedRole) {
    this.sessionManager = sessionManager;
    this.jwtUtil = jwtUtil;
    this.objectMapper = objectMapper;
    this.locationBatchProcessor = locationBatchProcessor;
    this.messageBroker = messageBroker;
    this.redisTemplate = redisTemplate;
    this.endpoint = endpoint;
    this.expectedRole = expectedRole;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    log.info("WebSocket connection established: {} on {}", session.getId(), endpoint);
    session.getAttributes().put("connectionTime", System.currentTimeMillis());
    sessionManager.addSession(session, endpoint);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    WsSession wsSession = sessionManager.getSession(session.getId());
    if (wsSession == null) {
      session.close(CloseStatus.SERVER_ERROR);
      return;
    }

    // Check message size
    if (message.getPayloadLength() > MAX_MESSAGE_SIZE) {
      sendError(session, "MESSAGE_TOO_LARGE", "Message exceeds 4KB limit");
      session.close(new CloseStatus(1009, "Message too large"));
      return;
    }

    // Rate limiting
    if (!wsSession.checkRateLimit()) {
      sendError(session, "RATE_LIMIT_EXCEEDED", "Maximum 5 messages per second");
      session.close(new CloseStatus(1008, "Rate limit exceeded"));
      return;
    }

    wsSession.updateLastMessageTime();

    try {
      ClientMessage clientMessage =
          objectMapper.readValue(message.getPayload(), ClientMessage.class);

      // Check authentication timeout only if not authenticated and not an auth message
      if (!wsSession.isAuthenticated() && !ClientMessage.TYPE_AUTH.equals(clientMessage.type())) {
        Duration timeSinceConnection =
            Duration.between(
                Instant.ofEpochMilli((Long) session.getAttributes().get("connectionTime")),
                Instant.now());

        if (timeSinceConnection.getSeconds() > AUTH_TIMEOUT_SECONDS) {
          sendError(session, "AUTH_TIMEOUT", "Authentication required within 5 seconds");
          session.close(new CloseStatus(1008, "Authentication timeout"));
          return;
        }
      }

      handleClientMessage(wsSession, clientMessage);
    } catch (Exception e) {
      log.error("Failed to parse message from {}", session.getId(), e);
      sendError(session, "INVALID_MESSAGE", "Failed to parse JSON message");
    }
  }

  private void handleClientMessage(WsSession wsSession, ClientMessage message) throws IOException {
    switch (message.type()) {
      case ClientMessage.TYPE_AUTH -> handleAuth(wsSession, message);
      case ClientMessage.TYPE_LOCATION -> handleLocation(wsSession, message);
      case ClientMessage.TYPE_ACCEPT -> handleAccept(wsSession, message);
      default ->
          sendError(
              wsSession.getSession(), "UNKNOWN_TYPE", "Unknown message type: " + message.type());
    }
  }

  private void handleAuth(WsSession wsSession, ClientMessage message) throws IOException {
    if (wsSession.isAuthenticated()) {
      sendError(wsSession.getSession(), "ALREADY_AUTHENTICATED", "Session already authenticated");
      return;
    }

    if (message.token() == null || message.token().isBlank()) {
      sendError(wsSession.getSession(), "MISSING_TOKEN", "Token is required");
      wsSession.getSession().close(new CloseStatus(1008, "Missing token"));
      return;
    }

    try {
      String userId = jwtUtil.extractUserId(message.token());
      String role = jwtUtil.extractRole(message.token());

      // Verify role matches endpoint
      if (!expectedRole.equals(role)) {
        sendError(
            wsSession.getSession(),
            "INVALID_ROLE",
            "Role " + role + " cannot connect to " + endpoint);
        wsSession.getSession().close(new CloseStatus(1008, "Invalid role for endpoint"));
        return;
      }

      sessionManager.authenticateSession(wsSession.getSessionId(), userId, role);
      log.info(
          "Session {} authenticated as user {} with role {}",
          wsSession.getSessionId(),
          userId,
          role);

    } catch (Exception e) {
      log.warn(
          "Authentication failed for session {}: {}", wsSession.getSessionId(), e.getMessage());
      sendError(wsSession.getSession(), "AUTH_FAILED", "Invalid or expired token");
      wsSession.getSession().close(new CloseStatus(1008, "Authentication failed"));
    }
  }

  private void handleLocation(WsSession wsSession, ClientMessage message) throws IOException {
    if (!wsSession.isAuthenticated()) {
      sendError(wsSession.getSession(), "NOT_AUTHENTICATED", "Authentication required");
      return;
    }

    if (!"DRIVER".equals(wsSession.getRole())) {
      sendError(wsSession.getSession(), "FORBIDDEN", "Only drivers can send location updates");
      return;
    }

    if (message.lat() == null || message.lng() == null) {
      sendError(wsSession.getSession(), "INVALID_LOCATION", "Latitude and longitude required");
      return;
    }

    // Validate coordinates
    if (message.lat() < -90 || message.lat() > 90 || message.lng() < -180 || message.lng() > 180) {
      sendError(wsSession.getSession(), "INVALID_COORDINATES", "Invalid latitude or longitude");
      return;
    }

    LocationUpdate update = new LocationUpdate(wsSession.getUserId(), message.lat(), message.lng());

    locationBatchProcessor.enqueue(update);
    log.debug(
        "Location update queued for driver {}: {}, {}",
        wsSession.getUserId(),
        message.lat(),
        message.lng());

    // Check if driver has an active customer
    String customerKey = "driver:" + wsSession.getUserId() + ":customer";
    String customerId = redisTemplate.opsForValue().get(customerKey);

    if (customerId != null) {
      Map<String, Object> data = new HashMap<>();
      data.put("lat", message.lat());
      data.put("lng", message.lng());
      data.put("sent_at", message.sentAt() != null ? message.sentAt() : System.currentTimeMillis());

      ServerMessage driverLocationMsg = ServerMessage.driverLocation(data);
      String json = objectMapper.writeValueAsString(driverLocationMsg);
      messageBroker.sendToUser(customerId, json);

      log.debug("Forwarded driver {} location to customer {}", wsSession.getUserId(), customerId);
    }
  }

  private void handleAccept(WsSession wsSession, ClientMessage message) throws IOException {
    if (!wsSession.isAuthenticated()) {
      sendError(wsSession.getSession(), "NOT_AUTHENTICATED", "Authentication required");
      return;
    }

    if (message.tripId() == null || message.tripId().isBlank()) {
      sendError(wsSession.getSession(), "MISSING_TRIP_ID", "Trip ID required");
      return;
    }

    // TODO: Forward to core service for trip acceptance
    log.info("Driver {} accepted trip {}", wsSession.getUserId(), message.tripId());
  }

  @Override
  protected void handlePongMessage(WebSocketSession session, PongMessage message) {
    WsSession wsSession = sessionManager.getSession(session.getId());
    if (wsSession != null) {
      wsSession.updateLastPongTime();
      log.debug("Received pong from session {}", session.getId());
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    log.info("WebSocket connection closed: {} with status ", session.getId(), status);
    sessionManager.removeSession(session.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.error("WebSocket transport error for session {}", session.getId(), exception);
    sessionManager.removeSession(session.getId());
  }

  private void sendError(WebSocketSession session, String code, String message) {
    try {
      ServerMessage errorMsg = ServerMessage.error(code, message);
      String json = objectMapper.writeValueAsString(errorMsg);
      session.sendMessage(new TextMessage(json));
    } catch (IOException e) {
      log.error("Failed to send error message to session {}", session.getId(), e);
    }
  }
}
