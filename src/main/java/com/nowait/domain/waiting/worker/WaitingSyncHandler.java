package com.nowait.domain.waiting.worker;

import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.redis.WaitingTokenData;
import com.nowait.domain.waiting.repository.WaitingRepository;
import com.nowait.domain.waiting.type.WaitingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/*
 * Worker 핸들러 — token 1건의 현재 Redis 상태를 DB 에 멱등하게 반영한다.
 *
 * 원리:
 *   - Redis Hash = source of truth
 *   - 메시지의 "op" 종류와 무관하게 항상 Hash 의 현재 상태로 upsert
 *   - 메시지 중복/순서뒤바뀜에도 결과 동일 (idempotent)
 *
 * Worker Pod 전용 — API Pod 에는 로드되지 않는다.
 */
@Slf4j
@Component
@Profile("waiting-worker")
@RequiredArgsConstructor
public class WaitingSyncHandler {

  private final WaitingRepository waitingRepository;
  private final WaitingRedisLuaExecutor waitingRedis;

  /*
   * @return true 면 정상 처리, false 면 일시적 오류 (재시도 대상).
   *         INSERT 했지만 Redis 에 데이터가 사라진 (TTL 만료 등) 경우는 정상으로 간주.
   */
  @Transactional
  public boolean sync(String token) {
    WaitingTokenData data = waitingRedis.findByToken(token);

    // Redis 의 Hash 가 사라진 경우: 이미 만료된 옛 메시지 → drop
    if (data == null) {
      log.warn("Sync skipped — Redis hash missing. token={}", token);
      return true;
    }

    LocalDateTime registeredAt = toLdt(data.registeredAt());
    LocalDateTime calledAt = toLdtNullable(data.calledAt());
    LocalDateTime enteredAt = toLdtNullable(data.enteredAt());
    LocalDateTime canceledAt = toLdtNullable(data.canceledAt());

    waitingRepository.findByWaitingToken(token).ifPresentOrElse(
        existing -> existing.syncFromRedis(data.status(), calledAt, enteredAt, canceledAt),
        () -> {
          Waiting entity = Waiting.register(
              token,
              data.userId(),
              data.restaurantId(),
              data.sessionId(),
              data.waitingNumber(),
              data.partySize(),
              registeredAt
          );
          // 등록 후 곧바로 상태 전이가 일어난 경우 (CANCELLED/CALLED/ENTERED) 한 번에 반영
          if (data.status() != WaitingStatus.WAITING) {
            entity.syncFromRedis(data.status(), calledAt, enteredAt, canceledAt);
          }
          waitingRepository.save(entity);
        }
    );

    log.debug("Synced. token={} status={}", token, data.status());
    return true;
  }

  private static LocalDateTime toLdt(long millis) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
  }

  private static LocalDateTime toLdtNullable(Long millis) {
    return millis == null ? null : toLdt(millis);
  }
}
