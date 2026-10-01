package com.ridehailing.core.controller;

import com.ridehailing.common.dto.ErrorResponse;
import com.ridehailing.core.dto.AuthResponse;
import com.ridehailing.core.dto.LoginRequest;
import com.ridehailing.core.dto.RegisterRequest;
import com.ridehailing.core.exception.EmailExistsException;
import com.ridehailing.core.exception.InvalidCredentialsException;
import com.ridehailing.core.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(EmailExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("EMAIL_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("INVALID_CREDENTIALS", e.getMessage()));
    }
}
