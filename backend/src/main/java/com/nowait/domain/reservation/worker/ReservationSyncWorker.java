package com.nowait.domain.reservation.worker;

import com.nowait.domain.reservation.redis.ReservationRedisKeys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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

  @Value("${worker.reservation.batch-size:50}")
  private int batchSize;

  @PostConstruct
  void onStartup() {
    log.info("ReservationSyncWorker activated. batchSize={}", batchSize);
    recoverInFlight();
  }

  /* 부팅 시 processing 큐 잔존 메시지를 pending-sync 로 되돌림 (이전 Worker 사망 복구) */
  private void recoverInFlight() {
    Long size = redis.opsForList().size(ReservationRedisKeys.PROCESSING);
    if (size == null || size == 0) return;

    log.warn("Recovering {} in-flight reservation messages.", size);
    for (long i = 0; i < size; i++) {
      String token = redis.opsForList().rightPopAndLeftPush(
          ReservationRedisKeys.PROCESSING, ReservationRedisKeys.PENDING_SYNC);
      if (token == null) break;
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
          redis.opsForList().remove(ReservationRedisKeys.PROCESSING, 1, token);
        } else {
          moveToDeadLetter(token, "handler returned false");
        }
        processed++;
      } catch (Exception e) {
        log.error("Failed to sync reservation. token={}. Moving to dead-letter.", token, e);
        moveToDeadLetter(token, e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
    if (processed > 0) {
      log.debug("Polled {} reservation messages.", processed);
    }
  }

  private void moveToDeadLetter(String token, String reason) {
    redis.opsForList().remove(ReservationRedisKeys.PROCESSING, 1, token);
    redis.opsForList().leftPush(ReservationRedisKeys.DEAD_LETTER,
        token + "|" + System.currentTimeMillis() + "|" + reason);
  }
}
