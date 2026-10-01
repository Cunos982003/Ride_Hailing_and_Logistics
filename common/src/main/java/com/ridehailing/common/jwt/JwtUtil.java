package com.ridehailing.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/** JWT token creation and validation utility. Secret read from JWT_SECRET env var. */
public class JwtUtil {
  private static final long EXPIRATION_MS = 3600000; // 1 hour

  private final SecretKey secretKey;

  public JwtUtil(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("JWT secret must not be blank");
    }
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /** Constructor for testing with explicit secret key. */
  protected JwtUtil(SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  /**
   * Create JWT with userId as subject and role claim.
   *
   * @param userId user identifier
   * @param role user role (e.g., "CUSTOMER", "DRIVER")
   * @return signed JWT token
   */
  public String createToken(String userId, String role) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(userId)
        .claim("role", role)
        .issuedAt(new Date(now))
        .expiration(new Date(now + EXPIRATION_MS))
        .signWith(secretKey)
        .compact();
  }

  /**
   * Generate token with custom expiration (for testing).
   */
  public String generateTokenWithExpiration(String userId, String role, Instant expiration) {
    return Jwts.builder()
        .subject(userId)
        .claim("role", role)
        .issuedAt(new Date())
        .expiration(Date.from(expiration))
        .signWith(secretKey)
        .compact();
  }

  /**
   * Validate JWT token and return true if valid.
   *
   * @param token JWT string
   * @return true if valid, false otherwise
   */
  public boolean validateToken(String token) {
    try {
      Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Extract userId from token.
   */
  public String extractUserId(String token) {
    Claims claims = Jwts.parser().verifyWith(secretKey).build()
        .parseSignedClaims(token).getPayload();
    return claims.getSubject();
  }

  /**
   * Extract role from token.
   */
  public String extractRole(String token) {
    Claims claims = Jwts.parser().verifyWith(secretKey).build()
        .parseSignedClaims(token).getPayload();
    return claims.get("role", String.class);
  }
}
