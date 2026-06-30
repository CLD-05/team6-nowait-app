package com.nowait.domain.waiting.worker;

import com.nowait.domain.waiting.monitoring.WaitingMetrics;
import com.nowait.domain.waiting.redis.WaitingRedisKeys;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
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
  private final WaitingMetrics waitingMetrics;

  @Value("${worker.waiting.batch-size:50}")
  private int batchSize;

  /* 한 번에 한 트랜잭션으로 묶어 처리할 청크 크기 (batchSize 를 이 단위로 분할) */
  @Value("${worker.waiting.chunk-size:25}")
  private int chunkSize;

  @PostConstruct
  void onStartup() {
    log.info("WaitingSyncWorker activated. batchSize={}, chunkSize={}", batchSize, chunkSize);
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
   * 처리 흐름 (배치 우선, 실패 시 단건 폴백):
   *   1. RPOPLPUSH 로 최대 batchSize 토큰을 pending-sync → processing 으로 원자 이동.
   *   2. 이를 chunkSize 단위로 나눠 청크별로 handler.syncBatch 를 호출(청크당 트랜잭션 1회).
   *   3. 청크 배치 성공 → 청크 전체를 processing 에서 ack(LREM).
   *      청크 배치 실패 → 그 청크만 기존 단건 처리(handler.sync)로 폴백.
   *
   * ack(LREM)는 반드시 DB 영속화 성공 "이후"에만 수행한다.
   * Worker 가 중간에 죽어도 processing 에 남은 토큰은 재기동 시 recoverInFlight 로 복구된다.
   */
  @Scheduled(fixedDelayString = "${worker.waiting.poll-interval-ms:500}")
  public void poll() {
    List<String> popped = new ArrayList<>(batchSize);
    for (int i = 0; i < batchSize; i++) {
      String token = redis.opsForList().rightPopAndLeftPush(
          WaitingRedisKeys.PENDING_SYNC, WaitingRedisKeys.PROCESSING);
      if (token == null) break;
      popped.add(token);
    }
    if (popped.isEmpty()) return;

    int effectiveChunk = Math.max(1, chunkSize);
    for (int start = 0; start < popped.size(); start += effectiveChunk) {
      int end = Math.min(start + effectiveChunk, popped.size());
      processChunk(popped.subList(start, end));
    }
    log.debug("Polled {} messages.", popped.size());
  }

  /*
   * 청크 1개 처리 — 배치 우선, 실패 시 단건 폴백.
   * 배치가 성공하면 청크 전체를 ack 하고, 예외가 나면 트랜잭션 롤백 후 단건으로 재처리한다.
   */
  private void processChunk(List<String> chunk) {
    try {
      handler.syncBatch(chunk);
      for (String token : chunk) {
        ack(token);
      }
      waitingMetrics.batchProcessed(chunk.size());
    } catch (Exception e) {
      log.warn("Batch chunk failed ({} tokens). Falling back to single-token. cause={}",
          chunk.size(), e.toString());
      waitingMetrics.batchFellBack();
      fallbackSingle(chunk);
    }
  }

  /*
   * 단건 폴백 — 청크 배치가 실패했을 때만 호출된다.
   * 성공 토큰은 정상 ack, 끝내 실패한 토큰만 기존 정책대로 dead-letter 로 보낸다.
   */
  private void fallbackSingle(List<String> chunk) {
    for (String token : chunk) {
      try {
        boolean ok = handler.sync(token);
        if (ok) {
          ack(token);
        } else {
          waitingMetrics.persistFailed();
          waitingMetrics.batchFailed();
          moveToDeadLetter(token, "handler returned false (fallback)");
        }
      } catch (Exception e) {
        log.error("Fallback sync failed token={}. Moving to dead-letter.", token, e);
        waitingMetrics.persistFailed();
        waitingMetrics.batchFailed();
        moveToDeadLetter(token, e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
  }

  /* DB 영속화 성공 후 processing 큐에서 토큰 1건 제거 (ack) */
  private void ack(String token) {
    redis.opsForList().remove(WaitingRedisKeys.PROCESSING, 1, token);
  }

  private void moveToDeadLetter(String token, String reason) {
    redis.opsForList().remove(WaitingRedisKeys.PROCESSING, 1, token);
    redis.opsForList().leftPush(WaitingRedisKeys.DEAD_LETTER,
        token + "|" + System.currentTimeMillis() + "|" + reason);
    waitingMetrics.deadLettered();
  }
}
