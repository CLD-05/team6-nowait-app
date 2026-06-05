package com.nowait.global.security.jwt;

import com.nowait.domain.user.type.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_ROLE = "role";

  private final String secret;
  private final long validitySeconds;
  private SecretKey key;

  public JwtTokenProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-validity-seconds}") long validitySeconds) {
    this.secret = secret;
    this.validitySeconds = validitySeconds;
  }

  @PostConstruct
  void init() {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String createToken(Long userId, String email, UserRole role) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + validitySeconds * 1000);

    return Jwts.builder()
        .setSubject(String.valueOf(userId))
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_ROLE, role.name())
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

  public Long getUserId(String token) {
    return Long.parseLong(parse(token).getSubject());
  }

  public String getEmail(String token) {
    return parse(token).get(CLAIM_EMAIL, String.class);
  }

  public UserRole getRole(String token) {
    return UserRole.valueOf(parse(token).get(CLAIM_ROLE, String.class));
  }

  private Claims parse(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(key)
        .build()
        .parseClaimsJws(token)
        .getBody();
  }
}