package com.ridehailing.user.config;

import com.ridehailing.common.security.JwtUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
  private String keyId;
  private String issuer;
  private String audience;
  private String privateKeyPath;
  private String publicKeyPath;
  private Duration accessTokenLifetime = Duration.ofMinutes(15);
  private Duration refreshTokenLifetime = Duration.ofDays(7);

  private JwtEncoder encoder;
  private JwtDecoder decoder;

  public void init() {
    try {
      RSAPublicKey publicKey = loadPublicKey(publicKeyPath);
      RSAPrivateKey privateKey = loadPrivateKey(privateKeyPath);
      this.encoder = JwtUtils.encoder(publicKey, privateKey, keyId);
      this.decoder = JwtUtils.decoder(publicKey, issuer, audience);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize JWT keys", e);
    }
  }

  private RSAPublicKey loadPublicKey(String path) throws Exception {
    String key = Files.readString(Path.of(path));
    String publicKeyPEM =
        key.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);
    X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return (RSAPublicKey) kf.generatePublic(spec);
  }

  private RSAPrivateKey loadPrivateKey(String path) throws Exception {
    String key = Files.readString(Path.of(path));
    String privateKeyPEM =
        key.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] decoded = Base64.getDecoder().decode(privateKeyPEM);
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) kf.generatePrivate(spec);
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public String getPrivateKeyPath() {
    return privateKeyPath;
  }

  public void setPrivateKeyPath(String privateKeyPath) {
    this.privateKeyPath = privateKeyPath;
  }

  public String getPublicKeyPath() {
    return publicKeyPath;
  }

  public void setPublicKeyPath(String publicKeyPath) {
    this.publicKeyPath = publicKeyPath;
  }

  public Duration getAccessTokenLifetime() {
    return accessTokenLifetime;
  }

  public void setAccessTokenLifetime(Duration accessTokenLifetime) {
    this.accessTokenLifetime = accessTokenLifetime;
  }

  public Duration getRefreshTokenLifetime() {
    return refreshTokenLifetime;
  }

  public void setRefreshTokenLifetime(Duration refreshTokenLifetime) {
    this.refreshTokenLifetime = refreshTokenLifetime;
  }

  public JwtEncoder getEncoder() {
    return encoder;
  }

  public JwtDecoder getDecoder() {
    return decoder;
  }
}
