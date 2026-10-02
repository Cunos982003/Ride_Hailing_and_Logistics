package com.ridehailing.gateway.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ServerMessage(
    @JsonProperty("t") String type, String code, String message, Map<String, Object> data) {
  public static final String TYPE_ERROR = "error";
  public static final String TYPE_OFFER = "offer";
  public static final String TYPE_DRIVER_LOCATION = "driver_location";
  public static final String TYPE_TRIP_UPDATE = "trip_update";

  public static ServerMessage error(String code, String message) {
    return new ServerMessage(TYPE_ERROR, code, message, null);
  }

  public static ServerMessage offer(Map<String, Object> offerData) {
    return new ServerMessage(TYPE_OFFER, null, null, offerData);
  }

  public static ServerMessage driverLocation(Map<String, Object> locationData) {
    return new ServerMessage(TYPE_DRIVER_LOCATION, null, null, locationData);
  }

  public static ServerMessage tripUpdate(Map<String, Object> tripData) {
    return new ServerMessage(TYPE_TRIP_UPDATE, null, null, tripData);
  }
}
