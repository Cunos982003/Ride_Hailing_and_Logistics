package com.ridehailing.common.jwt;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

  /**
   * Test JWT creation and validation with a directly provided secret key. Real usage reads from
   * JWT_SECRET env var.
   */
  @Test
  void createAndValidateToken_shouldWork() {
    // Create JwtUtil with constructor that accepts explicit secret for testing
    String testSecret = "test-secret-key-minimum-32-characters-long-for-hs256";
    SecretKey key = Keys.hmacShaKeyFor(testSecret.getBytes(StandardCharsets.UTF_8));

    JwtUtilTestable jwtUtil = new JwtUtilTestable(key);
    String token = jwtUtil.createToken("user123", "PASSENGER");

    assertNotNull(token);
    assertTrue(token.split("\\.").length == 3);

    assertTrue(jwtUtil.validateToken(token));
    assertEquals("user123", jwtUtil.extractUserId(token));
    assertEquals("PASSENGER", jwtUtil.extractRole(token));
  }

  /** Test helper that exposes secret key injection for testing. */
  private static class JwtUtilTestable extends JwtUtil {
    private final SecretKey testKey;

    JwtUtilTestable(SecretKey testKey) {
      super(testKey);
      this.testKey = testKey;
    }
  }
}
