package com.nowait.domain.waiting.worker;

import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.monitoring.WaitingMetrics;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  private final WaitingMetrics waitingMetrics;

  /*
   * 단건 동기화 — 폴백(배치 실패 시) 및 단독 처리 경로.
   *
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

    Optional<Waiting> existing = waitingRepository.findByWaitingToken(token);
    Waiting toInsert = upsertFromData(token, data, existing.orElse(null));
    if (toInsert != null) {
      waitingRepository.save(toInsert);
    }

    waitingMetrics.persistSucceeded(data.registeredAt());
    log.debug("Synced. token={} status={}", token, data.status());
    return true;
  }

  /*
   * 청크 배치 동기화 — 청크 내 토큰들을 "한 트랜잭션"으로 처리한다.
   *
   * 안전/멱등 설계:
   *   - 입력 토큰을 dedup 한다 (한 청크에 같은 토큰이 중복 유입돼도 1회만 upsert).
   *   - 기존 행은 findByWaitingTokenIn 으로 한 번에 조회(N+1 회피)해 dirty update.
   *   - 신규 행만 모아 saveAll 로 저장.
   *   - waitingToken 의 UNIQUE 제약 + token 기준 upsert 로 중복 행이 생기지 않는다.
   *
   * ⚠️ Waiting.id 는 GenerationType.IDENTITY 라 Hibernate INSERT 배치가 동작하지 않는다
   *    (saveAll 이어도 신규 INSERT 는 건별 실행). 다만 UPDATE 는 order_updates+batch_size
   *    로 묶이고, 무엇보다 "청크당 트랜잭션/커밋 1회"로 commit 오버헤드가 크게 줄어든다.
   *
   * 예외 발생 시 트랜잭션 전체가 롤백되며, 호출자(Worker)가 단건 폴백으로 재처리한다.
   */
  @Transactional
  public void syncBatch(List<String> tokens) {
    // 1. dedup — 순서 보존
    List<String> distinct = new ArrayList<>(new LinkedHashSet<>(tokens));

    // 2. 기존 행 일괄 조회 → token→entity 맵
    Map<String, Waiting> existingByToken = waitingRepository.findByWaitingTokenIn(distinct).stream()
        .collect(Collectors.toMap(Waiting::getWaitingToken, Function.identity()));

    List<Waiting> toInsert = new ArrayList<>();
    List<Long> persistedRegisteredAt = new ArrayList<>();

    for (String token : distinct) {
      WaitingTokenData data = waitingRedis.findByToken(token);
      if (data == null) {
        // Redis Hash 만료/소멸 — 단건 경로와 동일하게 drop (ack 대상이지만 DB 반영 없음)
        log.warn("Batch sync skipped — Redis hash missing. token={}", token);
        continue;
      }
      Waiting newEntity = upsertFromData(token, data, existingByToken.get(token));
      if (newEntity != null) {
        toInsert.add(newEntity);
      }
      persistedRegisteredAt.add(data.registeredAt());
    }

    if (!toInsert.isEmpty()) {
      waitingRepository.saveAll(toInsert);
    }

    // 기존 단건 경로와 동일 의미의 persist 성공/lag 메트릭을 토큰별로 유지
    for (Long registeredAtMillis : persistedRegisteredAt) {
      waitingMetrics.persistSucceeded(registeredAtMillis);
    }
    log.debug("Batch synced. tokens={}, distinct={}, inserted={}",
        tokens.size(), distinct.size(), toInsert.size());
  }

  /*
   * Redis 데이터로 행을 멱등 반영한다.
   *   - existing != null: 관리 엔티티에 상태를 덮어쓴다(트랜잭션 commit 시 flush). null 반환.
   *   - existing == null: 신규 엔티티를 만들어 반환한다(호출자가 save/saveAll).
   */
  private Waiting upsertFromData(String token, WaitingTokenData data, Waiting existing) {
    LocalDateTime calledAt = toLdtNullable(data.calledAt());
    LocalDateTime enteredAt = toLdtNullable(data.enteredAt());
    LocalDateTime canceledAt = toLdtNullable(data.canceledAt());

    if (existing != null) {
      existing.syncFromRedis(data.status(), calledAt, enteredAt, canceledAt);
      return null;
    }

    Waiting entity = Waiting.register(
        token,
        data.userId(),
        data.restaurantId(),
        data.sessionId(),
        data.waitingNumber(),
        data.partySize(),
        toLdt(data.registeredAt())
    );
    // 등록 후 곧바로 상태 전이가 일어난 경우 (CANCELLED/CALLED/ENTERED) 한 번에 반영
    if (data.status() != WaitingStatus.WAITING) {
      entity.syncFromRedis(data.status(), calledAt, enteredAt, canceledAt);
    }
    return entity;
  }

  private static LocalDateTime toLdt(long millis) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), com.nowait.global.common.TimeZones.KST);
  }

  private static LocalDateTime toLdtNullable(Long millis) {
    return millis == null ? null : toLdt(millis);
  }
}
