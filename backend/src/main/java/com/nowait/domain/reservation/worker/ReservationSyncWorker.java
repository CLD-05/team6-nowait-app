package com.nowait.domain.reservation.worker;

import com.nowait.domain.reservation.monitoring.ReservationMetrics;
import com.nowait.domain.reservation.redis.ReservationRedisKeys;
import jakarta.annotation.PostConstruct;
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

  @PostConstruct
  void onStartup() {
    log.info("ReservationSyncWorker activated. batchSize={}", batchSize);
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

  /* 주기적 폴링 — fixedDelay 로 이전 실행 종료 후 N ms 뒤 시작 */
  @Scheduled(fixedDelayString = "${worker.reservation.poll-interval-ms:500}")
  public void poll() {
    int processed = 0;
    for (int i = 0; i < batchSize; i++) {
      String token = redis.opsForList().rightPopAndLeftPush(
          ReservationRedisKeys.PENDING_SYNC, ReservationRedisKeys.PROCESSING);
      if (token == null) break;

      try {
        boolean ok = handler.sync(token);
        if (ok) {
          ack(token);
        } else {
          finishFailure(token, null, "handler returned false");
        }
      } catch (Exception e) {
        finishFailure(token, e, e.getClass().getSimpleName() + ": " + e.getMessage());
      }
      processed++;
    }
    if (processed > 0) {
      log.debug("Polled {} reservation messages.", processed);
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
