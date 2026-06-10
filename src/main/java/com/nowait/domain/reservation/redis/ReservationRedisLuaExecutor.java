package com.nowait.domain.reservation.redis;

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
import java.util.Set;
import java.util.UUID;

/*
 * 예약 도메인 Redis Lua 스크립트 실행기.
 *
 * - create / cancel / visit / no-show 4종 호출
 * - 결과 List<Object> 의 첫 요소가 성공(1)/실패(0)
 * - 실패 시 에러 코드 문자열을 ErrorCode 로 매핑해 BusinessException
 */
@Slf4j
@Component
public class ReservationRedisLuaExecutor {

  /* Hash/카운터 TTL — 예약은 미래 시각 + 여유. 우선 7일 고정 (장기 예약 시 재조정) */
  private static final Duration RESERVATION_TTL = Duration.ofDays(7);
  private static final long RESERVATION_TTL_SECONDS = RESERVATION_TTL.toSeconds();

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<List> createScript;
  private final RedisScript<List> cancelScript;
  private final RedisScript<List> visitScript;
  private final RedisScript<List> noShowScript;

  public ReservationRedisLuaExecutor(
      StringRedisTemplate redisTemplate,
      @Qualifier("reservationCreateScript") RedisScript<List> createScript,
      @Qualifier("reservationCancelScript") RedisScript<List> cancelScript,
      @Qualifier("reservationVisitScript") RedisScript<List> visitScript,
      @Qualifier("reservationNoShowScript") RedisScript<List> noShowScript) {
    this.redisTemplate = redisTemplate;
    this.createScript = createScript;
    this.cancelScript = cancelScript;
    this.visitScript = visitScript;
    this.noShowScript = noShowScript;
  }

  /* 예약 생성 — 성공 시 (token, createdAt) 반환 */
  public CreateResult create(
      Long userId, Long restaurantId, Long slotId,
      int headcount, int slotCapacity, long reservationTimeMillis) {

    String token = UUID.randomUUID().toString();
    long createdAt = System.currentTimeMillis();

    List<String> keys = List.of(
        ReservationRedisKeys.slotCount(slotId),
        ReservationRedisKeys.slotQueue(slotId),
        ReservationRedisKeys.token(token),
        ReservationRedisKeys.userSlot(userId, slotId),
        ReservationRedisKeys.NOSHOW_CANDIDATES,
        ReservationRedisKeys.PENDING_SYNC
    );

    List<?> result = redisTemplate.execute(
        createScript, keys,
        String.valueOf(userId),
        String.valueOf(restaurantId),
        String.valueOf(slotId),
        String.valueOf(headcount),
        String.valueOf(slotCapacity),
        token,
        String.valueOf(createdAt),
        String.valueOf(reservationTimeMillis),
        String.valueOf(RESERVATION_TTL_SECONDS)
    );

    if (isFailure(result)) {
      throw mapError(result, "create");
    }
    return new CreateResult(token, createdAt);
  }

  /* 예약 취소 — 본인 검증 포함 */
  public void cancel(String token, Long userId, Long slotId) {
    List<String> keys = List.of(
        ReservationRedisKeys.token(token),
        ReservationRedisKeys.slotQueue(slotId),
        ReservationRedisKeys.slotCount(slotId),
        ReservationRedisKeys.userSlot(userId, slotId),
        ReservationRedisKeys.NOSHOW_CANDIDATES,
        ReservationRedisKeys.PENDING_SYNC
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

  /* 방문 완료 (점주) */
  public void visit(String token, Long userId, Long slotId) {
    List<String> keys = List.of(
        ReservationRedisKeys.token(token),
        ReservationRedisKeys.slotQueue(slotId),
        ReservationRedisKeys.userSlot(userId, slotId),
        ReservationRedisKeys.NOSHOW_CANDIDATES,
        ReservationRedisKeys.PENDING_SYNC
    );

    List<?> result = redisTemplate.execute(
        visitScript, keys,
        token,
        String.valueOf(System.currentTimeMillis())
    );

    if (isFailure(result)) {
      throw mapError(result, "visit");
    }
  }

  /* 노쇼 처리 (스케줄러 또는 점주) */
  public void noShow(String token, Long userId, Long slotId) {
    List<String> keys = List.of(
        ReservationRedisKeys.token(token),
        ReservationRedisKeys.slotQueue(slotId),
        ReservationRedisKeys.userSlot(userId, slotId),
        ReservationRedisKeys.NOSHOW_CANDIDATES,
        ReservationRedisKeys.PENDING_SYNC
    );

    List<?> result = redisTemplate.execute(
        noShowScript, keys,
        token,
        String.valueOf(System.currentTimeMillis())
    );

    if (isFailure(result)) {
      throw mapError(result, "noShow");
    }
  }

  /* 토큰으로 예약 상세 조회 */
  public ReservationTokenData findByToken(String token) {
    Map<Object, Object> hash = redisTemplate.opsForHash()
        .entries(ReservationRedisKeys.token(token));
    return ReservationTokenData.fromHash(hash);
  }

  /* 슬롯의 현재 예약 점유 수 */
  public int getSlotCount(Long slotId) {
    String value = redisTemplate.opsForValue().get(ReservationRedisKeys.slotCount(slotId));
    return value == null ? 0 : Integer.parseInt(value);
  }

  /* 슬롯의 활성 토큰 목록 (생성 순서) */
  public List<String> listSlotTokens(Long slotId) {
    Set<String> tokens = redisTemplate.opsForZSet()
        .range(ReservationRedisKeys.slotQueue(slotId), 0, -1);
    return tokens == null ? Collections.emptyList() : new ArrayList<>(tokens);
  }

  /* 노쇼 스케줄러 — 예약시각 + grace 가 지난 토큰들 */
  public List<String> listNoShowCandidates(long maxScoreMillis) {
    Set<String> tokens = redisTemplate.opsForZSet()
        .rangeByScore(ReservationRedisKeys.NOSHOW_CANDIDATES, 0, maxScoreMillis);
    return tokens == null ? Collections.emptyList() : new ArrayList<>(tokens);
  }

  // --------- 내부 유틸 ---------

  private boolean isFailure(List<?> result) {
    if (result == null || result.isEmpty()) return true;
    return parseInt(result.get(0)) != 1;
  }

  private BusinessException mapError(List<?> result, String op) {
    String code = result.size() > 1 ? String.valueOf(result.get(1)) : "UNKNOWN";
    log.warn("Reservation Lua [{}] failed: {}", op, code);

    return switch (code) {
      case "DUPLICATE_RESERVATION"     -> new BusinessException(ErrorCode.DUPLICATE_RESERVATION);
      case "SLOT_FULL"                 -> new BusinessException(ErrorCode.SLOT_FULL);
      case "RESERVATION_NOT_FOUND"     -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
      case "ACCESS_DENIED"             -> new BusinessException(ErrorCode.RESERVATION_ACCESS_DENIED);
      case "ALREADY_PROCESSED"         -> new BusinessException(ErrorCode.ALREADY_CANCELLED_RESERVATION);
      case "INVALID_STATUS_TRANSITION" -> new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
      default                          -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    };
  }

  private int parseInt(Object o) {
    if (o instanceof Number n) return n.intValue();
    return Integer.parseInt(String.valueOf(o));
  }

  /* 생성 성공 시 반환 */
  public record CreateResult(String token, long createdAt) {}
}
