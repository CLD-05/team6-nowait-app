package com.nowait.domain.reservation.worker;

import com.nowait.domain.reservation.monitoring.ReservationMetrics;
import com.nowait.domain.reservation.redis.ReservationRedisKeys;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;

/*
 * Worker — reservation:pending-sync 큐를 폴링해서 DB 반영.
 *
 * 안전 처리 패턴 (Waiting Worker 와 동일):
 *   1. RPOPLPUSH pending-sync → processing  (atomic 처리 시작 표시)
 *   2. 핸들러 호출
 *   3. 성공: LREM processing 1 token
 *      실패: LREM 후 dead-letter 로 이동
 *
 * 멀티 Worker Pod 확장 시: RPOPLPUSH 가 원자라 동일 메시지 중복 처리 X.
 */
@Slf4j
@Component
@Profile("reservation-worker")
@RequiredArgsConstructor
public class ReservationSyncWorker {

  private final StringRedisTemplate redis;
  private final ReservationSyncHandler handler;
  private final ReservationMetrics reservationMetrics;

  @Value("${worker.reservation.batch-size:50}")
  private int batchSize;

  /*
   * 한 트랜잭션으로 묶어 처리할 청크 크기 (batchSize 를 이 단위로 분할).
   * ⚠️ 예약 워커는 슬롯 PESSIMISTIC_WRITE 락을 청크 동안 길게 쥐므로, hot 슬롯
   *    경합이 크면 이 값을 작게(예: 5~10) 두는 것이 안전하다.
   */
  @Value("${worker.reservation.chunk-size:25}")
  private int chunkSize;

  /*
   * 일시적 실패(락 타임아웃/교착/rollback) 시 DLQ 대신 재시도 큐로 되돌릴 최대 횟수.
   * 이 횟수를 초과하면 DLQ 로 넘긴다.
   */
  @Value("${worker.reservation.max-retry-attempts:5}")
  private int maxRetryAttempts;

  /* 토큰별 재시도 카운터 TTL — 성공/DLQ 후 남은 카운터를 자동 소멸시켜 누수 방지 */
  private static final Duration RETRY_COUNTER_TTL = Duration.ofMinutes(30);

  @PostConstruct
  void onStartup() {
    log.info("ReservationSyncWorker activated. batchSize={}, chunkSize={}", batchSize, chunkSize);
    recoverInFlight();
  }

  /*
   * 부팅 시 processing 큐 잔존 메시지를 pending-sync 로 되돌림 (이전 Worker 사망 복구).
   *
   * 멱등성 보완: 이미 DB 에 저장 완료된 토큰은 재처리할 필요가 없으므로 pending 으로
   * 되돌리지 않고 제거한다. 원자성을 위해 "먼저 pending 으로 이동(rightPopAndLeftPush)"한 뒤
   * 존재하면 그 사본을 제거한다 — 제거 직전 크래시가 나도 토큰은 pending 에 남아 멱등 sync 로
   * 안전하게 재처리되므로 유실되지 않는다.
   */
  private void recoverInFlight() {
    Long size = redis.opsForList().size(ReservationRedisKeys.PROCESSING);
    if (size == null || size == 0) return;

    log.warn("Recovering {} in-flight reservation messages.", size);
    for (long i = 0; i < size; i++) {
      String token = redis.opsForList().rightPopAndLeftPush(
          ReservationRedisKeys.PROCESSING, ReservationRedisKeys.PENDING_SYNC);
      if (token == null) break;

      if (handler.existsByReservationToken(token)) {
        redis.opsForList().remove(ReservationRedisKeys.PENDING_SYNC, 1, token);
        reservationMetrics.recoverSkippedExisting();
        log.info("Recovered in-flight reservation already in DB. Skip requeue. token={}", token);
      } else {
        reservationMetrics.recoveredInflight();
      }
    }
  }

  /*
   * 주기적 폴링 — 배치 우선, 실패 시 단건 폴백.
   *   1. RPOPLPUSH 로 최대 batchSize 토큰을 pending-sync → processing 으로 원자 이동.
   *   2. chunkSize 단위로 나눠 청크별 handler.syncBatch 호출(청크당 트랜잭션 1회).
   *   3. 청크 성공 → 청크 전체 ack(LREM). 실패 → 그 청크만 단건(sync)으로 폴백.
   * ack(LREM)는 반드시 DB 영속화 성공 이후에만 수행한다.
   */
  @Scheduled(fixedDelayString = "${worker.reservation.poll-interval-ms:500}")
  public void poll() {
    List<String> popped = new ArrayList<>(batchSize);
    for (int i = 0; i < batchSize; i++) {
      String token = redis.opsForList().rightPopAndLeftPush(
          ReservationRedisKeys.PENDING_SYNC, ReservationRedisKeys.PROCESSING);
      if (token == null) break;
      popped.add(token);
    }
    if (popped.isEmpty()) return;

    int effectiveChunk = Math.max(1, chunkSize);
    for (int start = 0; start < popped.size(); start += effectiveChunk) {
      int end = Math.min(start + effectiveChunk, popped.size());
      processChunk(popped.subList(start, end));
    }
    log.debug("Polled {} reservation messages.", popped.size());
  }

  /*
   * 청크 1개 처리 — 배치 우선, 실패 시 단건 폴백.
   * 배치 성공 시 청크 전체를 ack 하고, 예외가 나면 트랜잭션 롤백 후 단건으로 재처리한다.
   */
  private void processChunk(List<String> chunk) {
    try {
      handler.syncBatch(chunk);
      for (String token : chunk) {
        ack(token);
      }
      reservationMetrics.batchProcessed(chunk.size());
    } catch (Exception e) {
      log.warn("Reservation batch chunk failed ({} tokens). Falling back to single-token. cause={}",
          chunk.size(), e.toString());
      reservationMetrics.batchFellBack();
      fallbackSingle(chunk);
    }
  }

  /*
   * 단건 폴백 — 청크 배치가 실패했을 때만 호출된다.
   * 성공 토큰은 정상 ack, 실패 토큰은 finishFailure 가 멱등 성공/DLQ 를 판정한다.
   */
  private void fallbackSingle(List<String> chunk) {
    for (String token : chunk) {
      try {
        boolean ok = handler.sync(token);
        if (ok) {
          ack(token);
        } else {
          finishFailure(token, null, "handler returned false (fallback)");
        }
      } catch (Exception e) {
        finishFailure(token, e, e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
  }

  /*
   * 처리 실패의 최종 판정.
   *
   * at-least-once 구조(재시도/복구/파드 재기동/스케일다운, 동시 처리)에서는 같은
   * reservationToken 의 중복 INSERT 가 발생할 수 있고, DB 의 UNIQUE 제약이 이를 막아
   * DataIntegrityViolationException 을 던진다. 이는 "이미 누군가 저장했다"는 신호이므로
   * DLQ 대상이 아니다.
   *
   * 안전조건: UNIQUE 위반처럼 보여도 "실제 DB 에 해당 row 가 존재할 때만" 멱등 성공.
   *   - 중복키 위반 + row 존재 → 멱등 성공(ack, DLQ 없음, idempotent_skip 증가, 슬롯 미조정)
   *   - 그 외(진짜 저장 실패, 위반처럼 보이나 row 없음) → 기존 retry/DLQ 정책
   */
  private void finishFailure(String token, Exception cause, String reason) {
    /* 1) 중복키 위반 + 실제 row 존재 → 이미 저장됨. 멱등 성공(ack). */
    if (cause != null && isDuplicateKeyViolation(cause) && handler.existsByReservationToken(token)) {
      reservationMetrics.idempotentDuplicateSkipped();
      log.warn("Reservation token already persisted. Treating as idempotent success. token={}", token);
      ack(token);
      return;
    }

    /*
     * 2) 일시적 오류(락 타임아웃/교착/rollback) → 임계치 이내면 재시도 큐로 되돌린다.
     *    고부하(800 VU) hot 슬롯 경합에서 나는 락 대기/롤백은 재처리하면 대부분 성공하므로
     *    곧바로 DLQ 로 보내지 않는다.
     */
    if (cause != null && isTransient(cause) && scheduleRetry(token, reason)) {
      return;
    }

    /* 3) 그 외(진짜 영속 실패) 또는 재시도 소진 → DLQ */
    if (cause != null) {
      log.error("Failed to sync reservation. token={}. Moving to dead-letter.", token, cause);
    } else {
      log.error("Reservation sync returned failure token={}. Moving to dead-letter.", token);
    }
    reservationMetrics.persistFailed();
    reservationMetrics.batchFailed();
    moveToDeadLetter(token, reason);
  }

  /*
   * 일시적 실패를 재시도 큐(pending-sync)로 되돌린다.
   * 토큰별 INCR 카운터로 시도 횟수를 세어 maxRetryAttempts 이내면 재시도(true),
   * 초과하면 카운터를 정리하고 false 를 반환해 호출자가 DLQ 로 넘기게 한다.
   */
  private boolean scheduleRetry(String token, String reason) {
    String key = ReservationRedisKeys.syncAttempts(token);
    Long attempts = redis.opsForValue().increment(key);
    redis.expire(key, RETRY_COUNTER_TTL);
    if (attempts != null && attempts <= maxRetryAttempts) {
      reservationMetrics.retryScheduled();
      log.warn("Transient reservation sync failure. Requeue for retry. token={} attempt={}/{} reason={}",
          token, attempts, maxRetryAttempts, reason);
      requeueForRetry(token);
      return true;
    }
    log.error("Transient reservation sync failure exceeded max retries ({}). token={} attempts={}",
        maxRetryAttempts, token, attempts);
    redis.delete(key);
    return false;
  }

  /* processing 에서 제거하고 pending-sync 로 되돌려 다음 poll 에서 재처리되게 한다. */
  private void requeueForRetry(String token) {
    redis.opsForList().remove(ReservationRedisKeys.PROCESSING, 1, token);
    redis.opsForList().leftPush(ReservationRedisKeys.PENDING_SYNC, token);
  }

  /*
   * 재시도 가치가 있는 "일시적" 오류인지 판정한다.
   *   - UnexpectedRollbackException : 참여 트랜잭션이 rollback-only 로 마킹된 뒤 커밋 실패
   *   - TransactionSystemException  : 커밋/롤백 처리 중 시스템 오류
   *   - TransientDataAccessException: 락 타임아웃/교착/직렬화 실패 계열
   *     (Pessimistic/CannotAcquireLock/QueryTimeout 모두 이 하위 타입)
   *   - 메시지에 deadlock / lock wait timeout 등 흔적이 있는 경우
   */
  private boolean isTransient(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof UnexpectedRollbackException
          || t instanceof TransactionSystemException
          || t instanceof TransientDataAccessException) {
        return true;
      }
      String msg = t.getMessage();
      if (msg != null) {
        String low = msg.toLowerCase();
        if (low.contains("deadlock")
            || low.contains("lock wait timeout")
            || low.contains("could not serialize")
            || low.contains("rolled back")) {
          return true;
        }
      }
    }
    return false;
  }

  /*
   * DB UNIQUE 제약 위반(중복키)인지 판정한다.
   * Spring 의 DataIntegrityViolationException 계열이거나, cause 체인 메시지에
   * "duplicate" / "reservation_token" 흔적이 있으면 중복키로 본다.
   */
  private boolean isDuplicateKeyViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof DataIntegrityViolationException) {
        return true;
      }
      String msg = t.getMessage();
      if (msg != null) {
        String lower = msg.toLowerCase();
        if (lower.contains("duplicate entry")
            || lower.contains("duplicate key")
            || lower.contains("reservation_token")) {
          return true;
        }
      }
    }
    return false;
  }

  /* DB 영속화 성공/존재확인 후 processing 큐에서 토큰 1건 제거 (ack) */
  private void ack(String token) {
    redis.opsForList().remove(ReservationRedisKeys.PROCESSING, 1, token);
  }

  private void moveToDeadLetter(String token, String reason) {
    redis.opsForList().remove(ReservationRedisKeys.PROCESSING, 1, token);
    redis.opsForList().leftPush(ReservationRedisKeys.DEAD_LETTER,
        token + "|" + System.currentTimeMillis() + "|" + reason);
    reservationMetrics.deadLettered();
  }
}
