package com.nowait.domain.waiting.redis;

import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
  private final RedisScript<List> callScript;
  private final RedisScript<List> cancelByOwnerScript;

  public WaitingRedisLuaExecutor(
      StringRedisTemplate redisTemplate,
      @Qualifier("waitingRegisterScript") RedisScript<List> registerScript,
      @Qualifier("waitingCancelScript") RedisScript<List> cancelScript,
      @Qualifier("waitingEnterScript") RedisScript<List> enterScript,
      @Qualifier("waitingCallScript") RedisScript<List> callScript,
      @Qualifier("waitingCancelByOwnerScript") RedisScript<List> cancelByOwnerScript) {
    this.redisTemplate = redisTemplate;
    this.registerScript = registerScript;
    this.cancelScript = cancelScript;
    this.enterScript = enterScript;
    this.callScript = callScript;
    this.cancelByOwnerScript = cancelByOwnerScript;
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
        WaitingRedisKeys.userActive(userId, restaurantId),
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
  public void cancel(String token, Long userId, Long sessionId, Long restaurantId) {
    List<String> keys = List.of(
        WaitingRedisKeys.token(token),
        WaitingRedisKeys.queue(sessionId),
        WaitingRedisKeys.count(sessionId),
        WaitingRedisKeys.userActive(userId, restaurantId),
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
  public void enter(String token, Long sessionId, Long userId, Long restaurantId) {
    List<String> keys = List.of(
        WaitingRedisKeys.token(token),
        WaitingRedisKeys.queue(sessionId),
        WaitingRedisKeys.count(sessionId),
        WaitingRedisKeys.userActive(userId, restaurantId),
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

  /* 점주 호출 (WAITING → CALLED) */
  public void call(String token) {
    List<String> keys = List.of(
        WaitingRedisKeys.token(token),
        WaitingRedisKeys.PENDING_SYNC
    );

    List<?> result = redisTemplate.execute(
        callScript, keys,
        token,
        String.valueOf(System.currentTimeMillis())
    );

    if (isFailure(result)) {
      throw mapError(result, "call");
    }
  }

  /* 점주 강제 취소 (본인 검증 없음 — 호출 측에서 권한 검증) */
  public void cancelByOwner(String token, Long sessionId, Long userId, Long restaurantId) {
    List<String> keys = List.of(
        WaitingRedisKeys.token(token),
        WaitingRedisKeys.queue(sessionId),
        WaitingRedisKeys.count(sessionId),
        WaitingRedisKeys.userActive(userId, restaurantId),
        WaitingRedisKeys.PENDING_SYNC
    );

    List<?> result = redisTemplate.execute(
        cancelByOwnerScript, keys,
        token,
        String.valueOf(System.currentTimeMillis())
    );

    if (isFailure(result)) {
      throw mapError(result, "cancelByOwner");
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

  // --------- 세션 / 사용자 단위 헬퍼 ---------

  /* 세션 오픈 시 카운터 초기화 + 활성 세션 등록 (Worker 스캔용) */
  public void initSession(Long sessionId) {
    redisTemplate.opsForValue().set(
        WaitingRedisKeys.nextNumber(sessionId), "0", SESSION_TTL);
    redisTemplate.opsForValue().set(
        WaitingRedisKeys.count(sessionId), "0", SESSION_TTL);
    redisTemplate.opsForSet().add(
        WaitingRedisKeys.ACTIVE_SESSIONS, String.valueOf(sessionId));
  }

  /* 세션 마감 시 키 정리 + 활성 세션 해제 */
  public void clearSession(Long sessionId) {
    redisTemplate.delete(List.of(
        WaitingRedisKeys.nextNumber(sessionId),
        WaitingRedisKeys.count(sessionId),
        WaitingRedisKeys.queue(sessionId)
    ));
    redisTemplate.opsForSet().remove(
        WaitingRedisKeys.ACTIVE_SESSIONS, String.valueOf(sessionId));
  }

  /* Worker 의 타임아웃 스캔용 — 모든 활성 세션 ID */
  public List<Long> listActiveSessionIds() {
    Set<String> raw = redisTemplate.opsForSet().members(WaitingRedisKeys.ACTIVE_SESSIONS);
    if (raw == null || raw.isEmpty()) return Collections.emptyList();
    List<Long> ids = new ArrayList<>(raw.size());
    for (String s : raw) ids.add(Long.valueOf(s));
    return ids;
  }

  /* 현재 대기 팀 수 */
  public int getCount(Long sessionId) {
    String value = redisTemplate.opsForValue().get(WaitingRedisKeys.count(sessionId));
    return value == null ? 0 : Integer.parseInt(value);
  }

  /* 사용자의 모든 활성 웨이팅 토큰 (여러 식당 동시 등록 가능) */
  public List<String> findActiveTokensOf(Long userId) {
    Set<String> keys = redisTemplate.keys(WaitingRedisKeys.userActivePattern(userId));
    if (keys == null || keys.isEmpty()) return Collections.emptyList();

    List<String> tokens = redisTemplate.opsForValue().multiGet(keys);
    if (tokens == null) return Collections.emptyList();
    return tokens.stream().filter(Objects::nonNull).toList();
  }

  /* 세션의 활성 웨이팅 토큰 목록 (등록 순서) */
  public List<String> listActiveTokens(Long sessionId) {
    Set<String> tokens = redisTemplate.opsForZSet()
        .range(WaitingRedisKeys.queue(sessionId), 0, -1);
    return tokens == null ? Collections.emptyList() : new ArrayList<>(tokens);
  }

  /* 앞 N팀 알림 발송 마킹 (멱등성 보장) */
  public boolean markNearCallNotifiedIfAbsent(String token) {
    Boolean done = redisTemplate.opsForHash()
        .putIfAbsent(WaitingRedisKeys.token(token), "nearCallNotified", "true");
    return Boolean.TRUE.equals(done);  // true = 방금 마킹됨, false = 이미 마킹돼있었음
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
