package com.nowait.global.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 탈퇴(또는 차단)된 사용자를 매 요청마다 DB 조회 없이 거부하기 위한 Redis 마커.
 * key: withdrawn:user:{userId} → "1", TTL = Access 토큰 최대 유효시간.
 * 발급되어 있던 Access 토큰이 모두 자연 만료될 때쯤 이 키도 함께 소멸하므로 메모리 누수가 없다.
 *
 * <p>기존엔 {@link JwtAuthenticationFilter} 가 매 요청마다
 * {@code userRepository.findById(userId)} 로 isDeleted 를 확인했는데,
 * 이는 상태조회 polling 트래픽을 그대로 DB 커넥션 부하로 전환시켰다.
 * 탈퇴 시점에 이 마커만 심어두면 인증 필터는 Redis O(1) 조회로 차단할 수 있다.
 * (단일 토큰만 막는 {@link TokenBlacklist} 와 달리, 해당 유저의 모든 기기/토큰을 한 번에 차단한다.)
 */
@Component
public class WithdrawnUserCache {

  private static final String KEY_PREFIX = "withdrawn:user:";

  private final StringRedisTemplate redis;
  private final long accessTokenValiditySeconds;

  public WithdrawnUserCache(
      StringRedisTemplate redis,
      @Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds) {
    this.redis = redis;
    this.accessTokenValiditySeconds = accessTokenValiditySeconds;
  }

  /** 탈퇴/차단 시 호출 — 이미 발급된 Access 토큰이 만료될 때까지 차단 마커를 유지한다. */
  public void markWithdrawn(Long userId) {
    redis.opsForValue().set(key(userId), "1", Duration.ofSeconds(accessTokenValiditySeconds));
  }

  public boolean isWithdrawn(Long userId) {
    return Boolean.TRUE.equals(redis.hasKey(key(userId)));
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }
}
