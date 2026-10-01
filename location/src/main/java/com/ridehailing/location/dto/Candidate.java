package com.ridehailing.location.dto;

import java.util.UUID;

public record Candidate(
    UUID driverId,
    double distanceMeters
) {}
