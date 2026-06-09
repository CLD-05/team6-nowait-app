package com.nowait.domain.waiting.redis;

import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * 웨이팅 도메인의 Redis Lua 스크립트 실행기.
 *
 * - register / cancel / enter 3종 스크립트를 호출
 * - 결과는 List<Object> 로 반환되며 첫 요소가 성공(1)/실패(0) 플래그
 * - 실패 시 에러 코드 문자열을 ErrorCode 로 매핑해 BusinessException
 */
@Slf4j
@Component
public class WaitingRedisLuaExecutor {

  /* Hash/카운터 TTL — 당일 + 여유 1시간 */
  private static final Duration SESSION_TTL = Duration.ofHours(25);
  private static final long SESSION_TTL_SECONDS = SESSION_TTL.toSeconds();

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<List> registerScript;
  private final RedisScript<List> cancelScript;
  private final RedisScript<List> enterScript;

  public WaitingRedisLuaExecutor(
      StringRedisTemplate redisTemplate,
      @Qualifier("waitingRegisterScript") RedisScript<List> registerScript,
      @Qualifier("waitingCancelScript") RedisScript<List> cancelScript,
      @Qualifier("waitingEnterScript") RedisScript<List> enterScript) {
    this.redisTemplate = redisTemplate;
    this.registerScript = registerScript;
    this.cancelScript = cancelScript;
    this.enterScript = enterScript;
  }

  /* 등록 — 성공 시 (token, waitingNumber) 반환 */
  public RegisterResult register(
      Long userId, Long sessionId, Long restaurantId,
      int partySize, int maxWaitingCount) {

    String token = UUID.randomUUID().toString();
    long registeredAt = System.currentTimeMillis();

    List<String> keys = List.of(
        WaitingRedisKeys.nextNumber(sessionId),
        WaitingRedisKeys.count(sessionId),
        WaitingRedisKeys.queue(sessionId),
        WaitingRedisKeys.token(token),
        WaitingRedisKeys.userActive(userId),
        WaitingRedisKeys.PENDING_SYNC
    );

    List<?> result = redisTemplate.execute(
        registerScript, keys,
        String.valueOf(userId),
        String.valueOf(sessionId),
        String.valueOf(restaurantId),
        String.valueOf(partySize),
        String.valueOf(maxWaitingCount),
        token,
        String.valueOf(registeredAt),
        String.valueOf(SESSION_TTL_SECONDS)
    );

    if (isFailure(result)) {
      throw mapError(result, "register");
    }

    int waitingNumber = parseInt(result.get(1));
    return new RegisterResult(token, waitingNumber, registeredAt);
  }

  /* 취소 — 본인 검증 포함 */
  public void cancel(String token, Long userId, Long sessionId) {
    List<String> keys = List.of(
        WaitingRedisKeys.token(token),
        WaitingRedisKeys.queue(sessionId),
        WaitingRedisKeys.count(sessionId),
        WaitingRedisKeys.userActive(userId),
        WaitingRedisKeys.PENDING_SYNC
    );

    List<?> result = redisTemplate.execute(
        cancelScript, keys,
        token,
        String.valueOf(userId),
        String.valueOf(System.currentTimeMillis())
    );

    if (isFailure(result)) {
      throw mapError(result, "cancel");
    }
  }

  /* 입장 완료 (점주) */
  public void enter(String token, Long sessionId, Long userId) {
    List<String> keys = List.of(
        WaitingRedisKeys.token(token),
        WaitingRedisKeys.queue(sessionId),
        WaitingRedisKeys.count(sessionId),
        WaitingRedisKeys.userActive(userId),
        WaitingRedisKeys.PENDING_SYNC
    );

    List<?> result = redisTemplate.execute(
        enterScript, keys,
        token,
        String.valueOf(System.currentTimeMillis())
    );

    if (isFailure(result)) {
      throw mapError(result, "enter");
    }
  }

  /* 토큰으로 웨이팅 상세 조회 */
  public WaitingTokenData findByToken(String token) {
    Map<Object, Object> hash = redisTemplate.opsForHash().entries(WaitingRedisKeys.token(token));
    return WaitingTokenData.fromHash(hash);
  }

  /* 내 앞에 몇 팀 — ZRANK */
  public long aheadCount(Long sessionId, String token) {
    Long rank = redisTemplate.opsForZSet().rank(WaitingRedisKeys.queue(sessionId), token);
    return rank == null ? 0L : rank;
  }

  // --------- 내부 유틸 ---------

  private boolean isFailure(List<?> result) {
    if (result == null || result.isEmpty()) return true;
    Object flag = result.get(0);
    return parseInt(flag) != 1;
  }

  private BusinessException mapError(List<?> result, String op) {
    String code = result.size() > 1 ? String.valueOf(result.get(1)) : "UNKNOWN";
    log.warn("Waiting Lua [{}] failed: {}", op, code);

    return switch (code) {
      case "DUPLICATE_WAITING"         -> new BusinessException(ErrorCode.DUPLICATE_WAITING);
      case "WAITING_COUNT_EXCEEDED"    -> new BusinessException(ErrorCode.WAITING_COUNT_EXCEEDED);
      case "WAITING_NOT_FOUND"         -> new BusinessException(ErrorCode.WAITING_NOT_FOUND);
      case "ACCESS_DENIED"             -> new BusinessException(ErrorCode.ACCESS_DENIED);
      case "ALREADY_PROCESSED"         -> new BusinessException(ErrorCode.ALREADY_PROCESSED_WAITING);
      case "INVALID_STATUS_TRANSITION" -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
      default                          -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    };
  }

  private int parseInt(Object o) {
    if (o instanceof Number n) return n.intValue();
    return Integer.parseInt(String.valueOf(o));
  }

  /* 등록 성공 시 반환 */
  public record RegisterResult(String token, int waitingNumber, long registeredAt) {}
}
