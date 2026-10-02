package com.ridehailing.gateway.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClientMessage(
    @JsonProperty("t") String type,
    String token,
    Double lat,
    Double lng,
    @JsonProperty("sent_at") Long sentAt,
    @JsonProperty("trip_id") String tripId) {
  public static final String TYPE_AUTH = "auth";
  public static final String TYPE_LOCATION = "location";
  public static final String TYPE_ACCEPT = "accept";
}
