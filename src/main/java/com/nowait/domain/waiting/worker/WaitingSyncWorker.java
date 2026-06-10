package com.nowait.domain.waiting.worker;

import com.nowait.domain.waiting.redis.WaitingRedisKeys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * Worker — waiting:pending-sync 큐를 폴링해서 DB 에 반영한다.
 *
 * 안전 처리 패턴:
 *   1. RPOPLPUSH waiting:pending-sync waiting:processing
 *      → atomic 하게 처리 시작을 표시 (in-flight 보호)
 *   2. 핸들러 호출
 *   3. 성공: LREM waiting:processing 1 token
 *      실패: LREM 후 waiting:dead-letter 로 이동 (1회 재시도)
 *
 * 멀티 Worker Pod 확장 시: RPOPLPUSH 자체가 원자라 동일 메시지 중복 처리 X.
 *
 * Worker Pod 에서만 활성화 (@Profile("waiting-worker"))
 */
@Slf4j
@Component
@Profile("waiting-worker")
@RequiredArgsConstructor
public class WaitingSyncWorker {

  private final StringRedisTemplate redis;
  private final WaitingSyncHandler handler;

  @Value("${worker.waiting.batch-size:50}")
  private int batchSize;

  @PostConstruct
  void onStartup() {
    log.info("WaitingSyncWorker activated. batchSize={}", batchSize);
    recoverInFlight();
  }

  /*
   * 부팅 시 processing 큐에 남은 메시지를 pending-sync 로 되돌린다.
   * (이전 Worker 가 처리 도중 죽었을 때를 위한 복구)
   */
  private void recoverInFlight() {
    Long size = redis.opsForList().size(WaitingRedisKeys.PROCESSING);
    if (size == null || size == 0) return;

    log.warn("Recovering {} in-flight messages from processing queue.", size);
    for (long i = 0; i < size; i++) {
      String token = redis.opsForList().rightPopAndLeftPush(
          WaitingRedisKeys.PROCESSING, WaitingRedisKeys.PENDING_SYNC);
      if (token == null) break;
    }
  }

  /*
   * 주기적 폴링 — fixedDelay 로 이전 실행 종료 후 N ms 뒤 시작.
   *
   * 한 틱당 batchSize 만큼만 처리 → 너무 오래 한 스레드 점유 방지.
   * 큐가 비면 즉시 종료, 다음 틱 대기.
   */
  @Scheduled(fixedDelayString = "${worker.waiting.poll-interval-ms:500}")
  public void poll() {
    int processed = 0;
    for (int i = 0; i < batchSize; i++) {
      String token = redis.opsForList().rightPopAndLeftPush(
          WaitingRedisKeys.PENDING_SYNC, WaitingRedisKeys.PROCESSING);
      if (token == null) break;

      try {
        boolean ok = handler.sync(token);
        if (ok) {
          redis.opsForList().remove(WaitingRedisKeys.PROCESSING, 1, token);
        } else {
          moveToDeadLetter(token, "handler returned false");
        }
        processed++;
      } catch (Exception e) {
        log.error("Failed to sync token={}. Moving to dead-letter.", token, e);
        moveToDeadLetter(token, e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
    if (processed > 0) {
      log.debug("Polled {} messages.", processed);
    }
  }

  private void moveToDeadLetter(String token, String reason) {
    redis.opsForList().remove(WaitingRedisKeys.PROCESSING, 1, token);
    redis.opsForList().leftPush(WaitingRedisKeys.DEAD_LETTER,
        token + "|" + System.currentTimeMillis() + "|" + reason);
  }
}
