package com.ridehailing.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridehailing.common.error.ApiException;
import com.ridehailing.common.error.ErrorCode;
import com.ridehailing.user.config.JwtProperties;
import com.ridehailing.user.domain.Role;
import com.ridehailing.user.domain.User;
import com.ridehailing.user.dto.AuthResponse;
import com.ridehailing.user.dto.LoginRequest;
import com.ridehailing.user.dto.RegisterRequest;
import com.ridehailing.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtProperties jwtProperties;
  @InjectMocks private AuthService authService;

  private User testUser;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId(UUID.randomUUID());
    testUser.setPhoneNumber("+84901234567");
    testUser.setPasswordHash("$2a$10$hashedpassword");
    testUser.setFullName("Test User");
    testUser.setRole(Role.CUSTOMER);
    testUser.setCreatedAt(Instant.now());
    testUser.setUpdatedAt(Instant.now());
  }

  @Test
  void shouldRegisterNewUser() {
    RegisterRequest request =
        new RegisterRequest(
            "+84901234567", "password123", "Test User", "test@example.com", Role.CUSTOMER);

    when(userRepository.existsByPhoneNumber(request.phoneNumber())).thenReturn(false);
    when(passwordEncoder.encode(request.password())).thenReturn("$2a$10$hashedpassword");
    when(userRepository.save(any(User.class))).thenReturn(testUser);

    authService.register(request);

    verify(userRepository).save(any(User.class));
    verify(passwordEncoder).encode(request.password());
  }

  @Test
  void shouldThrowConflictWhenPhoneNumberExists() {
    RegisterRequest request =
        new RegisterRequest("+84901234567", "password123", "Test User", null, Role.CUSTOMER);

    when(userRepository.existsByPhoneNumber(request.phoneNumber())).thenReturn(true);

    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
  }

  @Test
  void shouldLoginWithValidCredentials() {
    LoginRequest request = new LoginRequest("+84901234567", "password123");

    when(userRepository.findByPhoneNumber(request.phoneNumber()))
        .thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(true);

    authService.login(request);

    verify(userRepository).findByPhoneNumber(request.phoneNumber());
    verify(passwordEncoder).matches(request.password(), testUser.getPasswordHash());
  }

  @Test
  void shouldThrowUnauthorizedWhenUserNotFound() {
    LoginRequest request = new LoginRequest("+84999999999", "password123");

    when(userRepository.findByPhoneNumber(request.phoneNumber())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
  }

  @Test
  void shouldThrowUnauthorizedWhenPasswordIncorrect() {
    LoginRequest request = new LoginRequest("+84901234567", "wrongpassword");

    when(userRepository.findByPhoneNumber(request.phoneNumber()))
        .thenReturn(Optional.of(testUser));
    when(passwordEncoder.matches(request.password(), testUser.getPasswordHash())).thenReturn(false);

    assertThatThrownBy(() -> authService.login(request))
        .isInstanceOf(ApiException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
  }

  @Test
  void shouldGetUserProfile() {
    when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

    var profile = authService.getProfile(testUser.getId());

    assertThat(profile.id()).isEqualTo(testUser.getId());
    assertThat(profile.phoneNumber()).isEqualTo(testUser.getPhoneNumber());
    assertThat(profile.role()).isEqualTo(Role.CUSTOMER);
  }
}
