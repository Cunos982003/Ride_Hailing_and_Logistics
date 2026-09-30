package com.ridehailing.user.dto;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {}
