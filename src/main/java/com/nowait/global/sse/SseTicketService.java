package com.nowait.global.sse;

import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/*
 * SSE 연결용 단발 티켓 관리자.
 *
 * - JWT 헤더 인증된 사용자가 티켓 발급 요청 → UUID 발급 + Redis 저장(TTL 10초, 1회용)
 * - SSE 연결 시 ticket 쿼리파라미터로 전달 → consume() 으로 검증 + 즉시 폐기
 * - 쿼리스트링에 JWT 가 직접 노출되는 것을 방지하기 위한 임시 패스 역할
 */
@Service
@RequiredArgsConstructor
public class SseTicketService {

  private static final String KEY_PREFIX = "sse:ticket:";
  private static final Duration TTL = Duration.ofSeconds(30);

  private final StringRedisTemplate redisTemplate;

  /* 티켓 발급 (1회용, 10초 만료) */
  public String issue(Long userId) {
    String ticket = UUID.randomUUID().toString();
    redisTemplate.opsForValue().set(KEY_PREFIX + ticket, String.valueOf(userId), TTL);
    return ticket;
  }

  /* 티켓 검증 + 즉시 폐기. 유효하지 않으면 예외. */
  public Long consume(String ticket) {
    if (ticket == null || ticket.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_SSE_TICKET);
    }
    String userId = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_SSE_TICKET);
    }
    return Long.parseLong(userId);
  }
}
