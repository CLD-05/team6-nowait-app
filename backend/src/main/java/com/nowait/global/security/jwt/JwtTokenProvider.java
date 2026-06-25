package com.nowait.global.security.jwt;

import com.nowait.domain.user.type.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_TYPE = "type";

  public static final String TYPE_ACCESS = "ACCESS";
  public static final String TYPE_REFRESH = "REFRESH";

  private final String secret;

  @Getter
  private final long accessTokenValiditySeconds;

  @Getter
  private final long refreshTokenValiditySeconds;

  private SecretKey key;

  public JwtTokenProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
      @Value("${jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {
    this.secret = secret;
    this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
  }

  @PostConstruct
  void init() {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String createAccessToken(Long userId, String email, UserRole role) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + accessTokenValiditySeconds * 1000);

    return Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setSubject(String.valueOf(userId))
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_ROLE, role.name())
        .claim(CLAIM_TYPE, TYPE_ACCESS)
        .setIssuedAt(now)
        .setExpiration(expiry)
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public String createRefreshToken(Long userId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + refreshTokenValiditySeconds * 1000);

    return Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setSubject(String.valueOf(userId))
        .claim(CLAIM_TYPE, TYPE_REFRESH)
        .setIssuedAt(now)
        .setExpiration(expiry)
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  public boolean validate(String token) {
    try {
      parse(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("Invalid JWT: {}", e.getMessage());
      return false;
    }
  }

  public boolean isAccessToken(String token) {
    return TYPE_ACCESS.equals(getType(token));
  }

  public boolean isRefreshToken(String token) {
    return TYPE_REFRESH.equals(getType(token));
  }

  public Long getUserId(String token) {
    return Long.parseLong(parse(token).getSubject());
  }

  public String getEmail(String token) {
    return parse(token).get(CLAIM_EMAIL, String.class);
  }

  public UserRole getRole(String token) {
    return UserRole.valueOf(parse(token).get(CLAIM_ROLE, String.class));
  }

  public String getJti(String token) {
    return parse(token).getId();
  }

  public String getType(String token) {
    return parse(token).get(CLAIM_TYPE, String.class);
  }

  public Date getExpiration(String token) {
    return parse(token).getExpiration();
  }

  private Claims parse(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();
  }
}
