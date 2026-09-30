package com.ridehailing.user.service;

import com.ridehailing.common.error.ApiException;
import com.ridehailing.common.error.ErrorCode;
import com.ridehailing.common.security.JwtUtils;
import com.ridehailing.user.config.JwtProperties;
import com.ridehailing.user.domain.User;
import com.ridehailing.user.dto.AuthResponse;
import com.ridehailing.user.dto.LoginRequest;
import com.ridehailing.user.dto.RegisterRequest;
import com.ridehailing.user.dto.UserResponse;
import com.ridehailing.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProperties jwtProperties;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtProperties jwtProperties) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtProperties = jwtProperties;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    User user = new User();
    user.setPhoneNumber(request.phoneNumber());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setFullName(request.fullName());
    user.setEmail(request.email());
    user.setRole(request.role());
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    user = userRepository.save(user);
    return buildAuthResponse(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByPhoneNumber(request.phoneNumber())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ApiException(ErrorCode.UNAUTHORIZED);
    }
    return buildAuthResponse(user);
  }

  @Transactional(readOnly = true)
  public UserResponse getProfile(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    return toUserResponse(user);
  }

  private AuthResponse buildAuthResponse(User user) {
    String accessToken =
        JwtUtils.issue(
            jwtProperties.getEncoder(),
            jwtProperties.getKeyId(),
            jwtProperties.getIssuer(),
            jwtProperties.getAudience(),
            user.getId().toString(),
            jwtProperties.getAccessTokenLifetime());
    String refreshToken =
        JwtUtils.issue(
            jwtProperties.getEncoder(),
            jwtProperties.getKeyId(),
            jwtProperties.getIssuer(),
            jwtProperties.getAudience(),
            user.getId().toString(),
            jwtProperties.getRefreshTokenLifetime());
    return new AuthResponse(accessToken, refreshToken, toUserResponse(user));
  }

  private UserResponse toUserResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getPhoneNumber(),
        user.getFullName(),
        user.getEmail(),
        user.getRole(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
