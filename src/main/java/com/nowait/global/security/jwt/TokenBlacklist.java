package com.nowait.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 로그아웃된 Access Token jti를 보관해 매 요청마다 거부한다.
 * key: blacklist:{jti} → value: "1", TTL = Access 토큰 남은 만료 시간
 * 토큰이 자연 만료되면 Redis도 자동 삭제되어 메모리 누수가 없다.
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

  private static final String KEY_PREFIX = "blacklist:";

  private final StringRedisTemplate redis;

  public void add(String jti, long ttlSeconds) {
    if (ttlSeconds <= 0) {
      return; // 이미 만료된 토큰은 굳이 저장할 필요 없음
    }
    redis.opsForValue().set(key(jti), "1", Duration.ofSeconds(ttlSeconds));
  }

  public boolean contains(String jti) {
    return Boolean.TRUE.equals(redis.hasKey(key(jti)));
  }

  private String key(String jti) {
    return KEY_PREFIX + jti;
  }
}
