package com.nowait.domain.waiting.monitoring;

import com.nowait.domain.waiting.redis.WaitingRedisKeys;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.global.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/*
 * 웨이팅 도메인 메트릭을 Micrometer 에 등록 (ReservationMetrics와 동일한 패턴).
 *
 * (1) 큐/대기 gauge — scrape 시점에 Redis 조회:
 *   waiting_queue_pending / waiting_queue_processing / waiting_queue_deadletter
 *   waiting_active_sessions
 *
 * (2) Grafana "NoWait 핵심 운영 대시보드" 비즈니스/Worker 지표 (nowait_* 네임스페이스):
 *   nowait_waiting_register_success_total   대기 등록 성공
 *   nowait_waiting_register_failure_total   대기 등록 시스템 실패 (성공률 SLI 분모)
 *   nowait_capacity_full_total              수용 인원 초과 (정상 비즈니스 결과 — 실패 아님)
 *   nowait_duplicate_waiting_attempt_total  중복 대기 시도 (정상 비즈니스 결과 — 실패 아님)
 *   nowait_polling_requests_total           폴링 요청량
 *   nowait_worker_persist_success_total     Worker DB 저장 성공
 *   nowait_worker_persist_failure_total     Worker DB 저장 실패
 *   nowait_worker_persist_lag_seconds       Redis→DB 저장 지연 (histogram)
 *   nowait_worker_dlq_in_total              Dead Letter 유입량
 *   nowait_waiting_active_count             현재 대기 중 사용자 수 (gauge)
 *
 * 설계 메모:
 *   - 수용 초과/중복 시도는 "정상적인 비즈니스 결과"이므로 register_failure 로 집계하지 않는다.
 *     register_failure 는 INTERNAL_SERVER_ERROR / 예기치 못한 예외(예: Redis 장애)만 센다.
 *     → 대기 등록 성공률(P0 알림)이 "만석"이나 "중복 탭" 같은 정상 상황에 떨어지지 않게 한다.
 *   - 카운터는 API/Worker Pod 양쪽에 존재하나 이벤트 발생 측에서만 증가한다.
 *   - gauge 는 모든 Pod 가 동일 Redis 를 보므로 PromQL 에서 max() 로 집계한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingMetrics {

  private final MeterRegistry meterRegistry;
  private final StringRedisTemplate redis;
  private final WaitingRedisLuaExecutor waitingRedis;

  private Counter registerSuccess;
  private Counter registerFailure;
  private Counter capacityFull;
  private Counter duplicateAttempt;
  private Counter polling;
  private Counter workerPersistSuccess;
  private Counter workerPersistFailure;
  private Counter dlqIn;
  private Counter workerBatchProcessed;
  private Counter workerBatchFallback;
  private Counter workerBatchFailed;
  private Counter workerIdempotentSkip;
  private Counter workerRecoveredInflight;
  private Counter workerRecoverSkippedExisting;
  private Timer persistLag;

  @PostConstruct
  void register() {
    // (1) 기존 큐/세션 gauge
    meterRegistry.gauge("waiting.queue.pending", this,
        m -> m.safeLLen(WaitingRedisKeys.PENDING_SYNC));
    meterRegistry.gauge("waiting.queue.processing", this,
        m -> m.safeLLen(WaitingRedisKeys.PROCESSING));
    meterRegistry.gauge("waiting.queue.deadletter", this,
        m -> m.safeLLen(WaitingRedisKeys.DEAD_LETTER));
    meterRegistry.gauge("waiting.active.sessions", this,
        m -> m.safeSCard(WaitingRedisKeys.ACTIVE_SESSIONS));

    // (2) nowait_* 비즈니스/Worker 카운터·타이머
    registerSuccess = meterRegistry.counter("nowait.waiting.register.success");
    registerFailure = meterRegistry.counter("nowait.waiting.register.failure");
    capacityFull = meterRegistry.counter("nowait.capacity.full");
    duplicateAttempt = meterRegistry.counter("nowait.duplicate.waiting.attempt");
    polling = meterRegistry.counter("nowait.polling.requests");
    workerPersistSuccess = meterRegistry.counter("nowait.worker.persist.success");
    workerPersistFailure = meterRegistry.counter("nowait.worker.persist.failure");
    dlqIn = meterRegistry.counter("nowait.worker.dlq.in");
    // Worker 청크 배치 처리 전/후 비교용 (low-cardinality, 라벨 없음)
    //   batch.processed : 청크 배치 경로로 성공 반영된 토큰 수
    //   batch.fallback  : 배치 실패로 단건 처리로 폴백된 청크 수
    //   batch.failed    : 폴백에서도 끝내 실패해 dead-letter 된 토큰 수
    workerBatchProcessed = meterRegistry.counter("nowait.worker.batch.processed");
    workerBatchFallback = meterRegistry.counter("nowait.worker.batch.fallback");
    workerBatchFailed = meterRegistry.counter("nowait.worker.batch.failed");
    // Worker 멱등성 보완 (at-least-once 중복 처리 관측용, low-cardinality)
    //   idempotent.skip            : 같은 waitingToken 이 이미 DB 에 있어(동시 처리 UNIQUE 충돌)
    //                                중복 INSERT 를 멱등 성공으로 처리한 횟수 (DLQ 회피)
    //   recovered.inflight         : 재기동 시 in-flight 토큰을 pending 으로 복구한 횟수
    //   recover.skipped.existing   : 이미 DB 에 저장된 토큰이라 복구하지 않고 제거한 횟수
    workerIdempotentSkip = meterRegistry.counter("nowait.worker.idempotent.skip");
    workerRecoveredInflight = meterRegistry.counter("nowait.worker.recovered.inflight");
    workerRecoverSkippedExisting = meterRegistry.counter("nowait.worker.recover.skipped.existing");
    persistLag = Timer.builder("nowait.worker.persist.lag")
        .description("Redis->DB 비동기 저장 지연")
        .publishPercentileHistogram()
        .register(meterRegistry);

    // 현재 대기 중 사용자 수 = 활성 세션들의 count 합
    meterRegistry.gauge("nowait.waiting.active.count", this,
        WaitingMetrics::safeActiveWaitingCount);

    log.info("WaitingMetrics registered: queue gauges + nowait business/worker counters");
  }

  /* ===== 대기 등록 (API) ===== */

  public void registerSucceeded() {
    registerSuccess.increment();
  }

  /*
   * BusinessException 발생 시 호출. ErrorCode 별로 적절한 카운터에 분배한다.
   * 수용 초과/중복은 정상 비즈니스 결과 → 실패 카운터 증가 X.
   *
   * nowait_waiting_register_rejected_total{reason="<ERROR_CODE>"}:
   *   k6 가 보는 등록 4xx 응답을 "백엔드 비즈니스 거절 사유"별로 분해하기 위한 태그 카운터.
   *   기존 카운터(capacity_full/duplicate/failure)만으로는 집계되지 않던 사유
   *   (영업시간 외/휴무일/미오픈/세션 미수신 등)까지 모두 reason 라벨로 분리해
   *   "k6 4xx 가 왜 발생하는지" Grafana 에서 설명 가능하게 한다.
   *   ⚠️ reason 라벨은 반드시 ErrorCode enum(code.name())만 사용한다.
   *      자유 형식 예외 메시지를 라벨로 쓰면 Prometheus 라벨 카디널리티가 폭발한다.
   */
  public void registerRejected(ErrorCode code) {
    // (신규) 모든 비즈니스 거절을 사유별로 집계 — k6 4xx 분해용
    meterRegistry.counter("nowait.waiting.register.rejected", "reason", code.name())
        .increment();

    // (기존) Grafana 패널/알림 룰 호환을 위해 사유별 전용 카운터도 그대로 유지.
    //        일부 이벤트가 신규 태그 카운터와 기존 카운터에 모두 잡히는 것은 의도된 동작이다.
    if (code == ErrorCode.WAITING_COUNT_EXCEEDED) {
      capacityFull.increment();
    } else if (code == ErrorCode.DUPLICATE_WAITING) {
      duplicateAttempt.increment();
    } else if (code == ErrorCode.INTERNAL_SERVER_ERROR) {
      registerFailure.increment();
    }
    // 그 외(영업시간/휴무일/미오픈 등)는 정상적인 비즈니스 거절 → 기존 실패/전용 카운터엔 집계 X
    // (단, 위 신규 rejected_total{reason=...} 에는 항상 잡힌다)
  }

  /* 비즈니스 예외가 아닌 예기치 못한 시스템 실패 (Redis 장애 등) */
  public void registerSystemFailed() {
    registerFailure.increment();
  }

  /* ===== 폴링 (API) ===== */

  public void pollingObserved() {
    polling.increment();
  }

  /* ===== Worker (waiting-worker Pod) ===== */

  /* 저장 성공 — registeredAt(epoch millis) 로 Redis→DB 지연을 기록 */
  public void persistSucceeded(long registeredAtMillis) {
    workerPersistSuccess.increment();
    if (registeredAtMillis > 0) {
      long lagMillis = System.currentTimeMillis() - registeredAtMillis;
      if (lagMillis >= 0) {
        persistLag.record(Duration.ofMillis(lagMillis));
      }
    }
  }

  public void persistFailed() {
    workerPersistFailure.increment();
  }

  public void deadLettered() {
    dlqIn.increment();
  }

  /* 청크 배치 경로로 성공 반영된 토큰 수 (한 청크 성공 시 청크 크기만큼 증가) */
  public void batchProcessed(int count) {
    if (count > 0) {
      workerBatchProcessed.increment(count);
    }
  }

  /* 배치 실패로 단건 처리로 폴백된 청크 1건 */
  public void batchFellBack() {
    workerBatchFallback.increment();
  }

  /* 폴백에서도 끝내 실패해 dead-letter 된 토큰 1건 */
  public void batchFailed() {
    workerBatchFailed.increment();
  }

  /* 같은 waitingToken 이 이미 DB 에 존재 → 중복 INSERT 를 멱등 성공으로 처리(DLQ 회피) */
  public void idempotentDuplicateSkipped() {
    workerIdempotentSkip.increment();
  }

  /* 재기동 시 in-flight 토큰을 pending 으로 복구 */
  public void recoveredInflight() {
    workerRecoveredInflight.increment();
  }

  /* 이미 DB 에 저장된 토큰이라 복구하지 않고 processing 에서 제거 */
  public void recoverSkippedExisting() {
    workerRecoverSkippedExisting.increment();
  }

  /* ===== gauge 헬퍼 ===== */

  private double safeLLen(String key) {
    try {
      Long size = redis.opsForList().size(key);
      return size == null ? 0.0 : size.doubleValue();
    } catch (Exception e) {
      log.warn("Failed to read LLEN {} for metric: {}", key, e.getMessage());
      return Double.NaN;
    }
  }

  private double safeSCard(String key) {
    try {
      Long size = redis.opsForSet().size(key);
      return size == null ? 0.0 : size.doubleValue();
    } catch (Exception e) {
      log.warn("Failed to read SCARD {} for metric: {}", key, e.getMessage());
      return Double.NaN;
    }
  }

  private double safeActiveWaitingCount() {
    try {
      List<Long> sessionIds = waitingRedis.listActiveSessionIds();
      double total = 0.0;
      for (Long sessionId : sessionIds) {
        total += waitingRedis.getCount(sessionId);
      }
      return total;
    } catch (Exception e) {
      log.warn("Failed to compute active waiting count for metric: {}", e.getMessage());
      return Double.NaN;
    }
  }
}
