package com.ridehailing.location.dto;

public record NearbyRequest(double latitude, double longitude, double radiusKm, int limit) {}
