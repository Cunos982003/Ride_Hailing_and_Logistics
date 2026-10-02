package com.ridehailing.gateway.websocket.dto;

public record LocationUpdate(String driverId, double latitude, double longitude) {}
