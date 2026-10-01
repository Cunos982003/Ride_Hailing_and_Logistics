package com.ridehailing.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

/** JWT token creation and validation utility. Secret read from JWT_SECRET env var. */
public class JwtUtil {
  private static final long EXPIRATION_MS = 3600000; // 1 hour

  private final SecretKey secretKey;

  public JwtUtil() {
    String secret = System.getenv("JWT_SECRET");
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("JWT_SECRET environment variable must be set");
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
   * @param role user role (e.g., "PASSENGER", "DRIVER")
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
   * Parse and validate JWT token.
   *
   * @param token JWT string
   * @return claims if valid
   * @throws io.jsonwebtoken.JwtException if invalid or expired
   */
  public Claims validateToken(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }
}
