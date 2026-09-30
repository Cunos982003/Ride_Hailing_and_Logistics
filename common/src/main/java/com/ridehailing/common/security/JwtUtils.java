package com.ridehailing.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class JwtUtils {
  private JwtUtils() {}

  public static JwtEncoder encoder(RSAPublicKey publicKey, RSAPrivateKey privateKey, String keyId) {
    requireText(keyId, "keyId");
    requireKeySize(publicKey);
    RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
  }

  public static JwtDecoder decoder(RSAPublicKey publicKey, String issuer, String audience) {
    requireText(issuer, "issuer");
    requireText(audience, "audience");
    requireKeySize(publicKey);
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    OAuth2TokenValidator<Jwt> claims =
        token -> {
          Instant now = Instant.now();
          if (token.getSubject() == null
              || token.getSubject().isBlank()
              || token.getId() == null
              || token.getId().isBlank()
              || token.getIssuedAt() == null
              || token.getExpiresAt() == null
              || !token.getExpiresAt().isAfter(token.getIssuedAt())
              || token.getIssuedAt().isAfter(now.plusSeconds(30))
              || !token.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Invalid required claims", null));
          }
          return OAuth2TokenValidatorResult.success();
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new JwtTimestampValidator(Duration.ZERO),
            claims));
    return decoder;
  }

  public static String issue(
      JwtEncoder encoder,
      String keyId,
      String issuer,
      String audience,
      String subject,
      Duration lifetime) {
    requireText(keyId, "keyId");
    requireText(issuer, "issuer");
    requireText(audience, "audience");
    requireText(subject, "subject");
    if (lifetime == null || lifetime.isNegative() || lifetime.isZero()) {
      throw new IllegalArgumentException("JWT lifetime must be positive");
    }
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(subject)
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plus(lifetime))
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }

  private static void requireKeySize(RSAPublicKey key) {
    if (key == null || key.getModulus().bitLength() < 2048) {
      throw new IllegalArgumentException("RSA public key must be at least 2048 bits");
    }
  }
}
