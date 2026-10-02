package com.ridehailing.core.service;

import com.ridehailing.common.jwt.JwtUtil;
import com.ridehailing.core.dto.AuthResponse;
import com.ridehailing.core.dto.LoginRequest;
import com.ridehailing.core.dto.RegisterRequest;
import com.ridehailing.core.exception.EmailExistsException;
import com.ridehailing.core.exception.InvalidCredentialsException;
import com.ridehailing.core.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository userRepository;
  private final JwtUtil jwtUtil;
  private final BCryptPasswordEncoder passwordEncoder;

  public AuthService(UserRepository userRepository, JwtUtil jwtUtil) {
    this.userRepository = userRepository;
    this.jwtUtil = jwtUtil;
    this.passwordEncoder = new BCryptPasswordEncoder();
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    // Kiểm tra email đã tồn tại
    if (userRepository.existsByEmail(request.email())) {
      throw new EmailExistsException("Email already exists");
    }

    // Băm mật khẩu
    String passwordHash = passwordEncoder.encode(request.password());

    // Tạo user + wallet trong cùng transaction
    long userId =
        userRepository.createUserWithWallet(
            request.email(), passwordHash, request.fullName(), request.role());

    // Tạo JWT token
    String token = jwtUtil.createToken(String.valueOf(userId), request.role());

    return new AuthResponse(token);
  }

  public AuthResponse login(LoginRequest request) {
    // Tìm user theo email
    var user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

    // Verify password
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw new InvalidCredentialsException("Invalid credentials");
    }

    // Tạo JWT token
    String token = jwtUtil.createToken(String.valueOf(user.id()), user.role());

    return new AuthResponse(token);
  }
}
