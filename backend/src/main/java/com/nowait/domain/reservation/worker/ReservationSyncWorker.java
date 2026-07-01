package com.nowait.domain.reservation.worker;

import com.nowait.domain.reservation.monitoring.ReservationMetrics;
import com.nowait.domain.reservation.redis.ReservationRedisKeys;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    if (cause != null && isDuplicateKeyViolation(cause) && handler.existsByReservationToken(token)) {
      reservationMetrics.idempotentDuplicateSkipped();
      log.warn("Reservation token already persisted. Treating as idempotent success. token={}", token);
      ack(token);
      return;
    }

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
