package com.nowait.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Refresh Token Redis 저장소.
 * key: refresh:{userId} → value: refreshToken (1 user = 1 active refresh)
 * 새 로그인 / refresh rotation 시 덮어쓰기 → 이전 토큰 자동 무효화.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

  private static final String KEY_PREFIX = "refresh:";

  private final StringRedisTemplate redis;

  public void save(Long userId, String refreshToken, long ttlSeconds) {
    redis.opsForValue().set(key(userId), refreshToken, Duration.ofSeconds(ttlSeconds));
  }

  public String find(Long userId) {
    return redis.opsForValue().get(key(userId));
  }

  public boolean matches(Long userId, String refreshToken) {
    return Objects.equals(find(userId), refreshToken);
  }

  public void delete(Long userId) {
    redis.delete(key(userId));
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
