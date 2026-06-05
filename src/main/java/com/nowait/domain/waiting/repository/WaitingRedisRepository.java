package com.nowait.domain.waiting.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/*
   웨이팅 세션의 실시간 상태를 Redis 로 관리하는 리포지토리.

   키 네이밍:
     waiting:session:{sessionId}:next-number   대기번호 채번 카운터
     waiting:session:{sessionId}:count         현재 대기 팀 수

   동시성 안전: Redis 의 INCR/DECR 은 단일 명령 atomic.
*/
@Repository
@RequiredArgsConstructor
public class WaitingRedisRepository {

  private static final String NEXT_NUMBER_KEY_PREFIX = "waiting:session:";
  private static final String NEXT_NUMBER_KEY_SUFFIX = ":next-number";
  private static final String COUNT_KEY_PREFIX = "waiting:session:";
  private static final String COUNT_KEY_SUFFIX = ":count";

  /* 세션 데이터는 하루 + 여유 1시간 후 자동 만료. 세션 close 시 명시적 삭제도 함. */
  private static final Duration SESSION_TTL = Duration.ofHours(25);

  private final StringRedisTemplate redisTemplate;

  /* 세션 오픈 시 키 초기화 */
  public void initSession(Long sessionId) {
    ValueOperations<String, String> ops = redisTemplate.opsForValue();
    ops.set(nextNumberKey(sessionId), "0", SESSION_TTL);
    ops.set(countKey(sessionId), "0", SESSION_TTL);
  }

  /* 다음 대기번호 atomic 채번 (INCR 후 반환) */
  public int incrementAndGetNextNumber(Long sessionId) {
    Long result = redisTemplate.opsForValue().increment(nextNumberKey(sessionId));
    if (result == null) {
      throw new IllegalStateException("Failed to generate next waiting number for session: " + sessionId);
    }
    return result.intValue();
  }

  /* 현재 대기 팀 수 증가 */
  public int incrementCount(Long sessionId) {
    Long result = redisTemplate.opsForValue().increment(countKey(sessionId));
    return result == null ? 0 : result.intValue();
  }

  /* 현재 대기 팀 수 감소. 0 이하면 0으로 보정. */
  public int decrementCount(Long sessionId) {
    Long result = redisTemplate.opsForValue().decrement(countKey(sessionId));
    if (result == null)
      return 0;
    if (result < 0) {
      redisTemplate.opsForValue().set(countKey(sessionId), "0");
      return 0;
    }
    return result.intValue();
  }

  /* 현재 대기 팀 수 조회 */
  public int getCount(Long sessionId) {
    String value = redisTemplate.opsForValue().get(countKey(sessionId));
    return value == null ? 0 : Integer.parseInt(value);
  }

  /* 세션 마감 시 키 삭제 */
  public void clearSession(Long sessionId) {
    redisTemplate.delete(List.of(nextNumberKey(sessionId), countKey(sessionId)));
  }

  private String nextNumberKey(Long sessionId) {
    return NEXT_NUMBER_KEY_PREFIX + sessionId + NEXT_NUMBER_KEY_SUFFIX;
  }

  private String countKey(Long sessionId) {
    return COUNT_KEY_PREFIX + sessionId + COUNT_KEY_SUFFIX;
  }
}