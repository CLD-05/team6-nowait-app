package com.nowait.domain.waiting.worker;

import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.redis.WaitingTokenData;
import com.nowait.domain.waiting.type.WaitingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/*
 * CALLED 상태에서 N분 동안 입장이 없으면 자동으로 점주 취소 처리한다.
 *
 * 동작:
 *   - 매 분 active-sessions Set 스캔
 *   - 각 세션 큐의 토큰들 중 status=CALLED && calledAt < (now - timeout) 인 것을 cancelByOwner Lua 로 취소
 *   - 취소 시 자동으로 pending-sync 에 들어가 WaitingSyncWorker 가 DB 반영
 *
 * 다중 Worker Pod 안전성:
 *   - Redis SETNX 분산락으로 한 번에 한 Pod 만 스캔
 *   - 락 TTL 50초 — 스캔이 어떤 이유로 멈춰도 자동 해제
 */
@Slf4j
@Component
@Profile("waiting-worker")
@RequiredArgsConstructor
public class WaitingCallTimeoutScheduler {

  private static final String LOCK_KEY = "waiting:scheduler:lock:called-timeout";
  private static final Duration LOCK_TTL = Duration.ofSeconds(50);

  /* Pod 식별자 — 락 소유자 검증용 (TTL 만료 후 다른 Pod 의 락을 실수로 해제하지 않기 위함) */
  private final String podId = UUID.randomUUID().toString();

  private final WaitingRedisLuaExecutor waitingRedis;
  private final StringRedisTemplate redis;

  @Value("${waiting.call-timeout-minutes:10}")
  private long callTimeoutMinutes;

  @Scheduled(cron = "${worker.waiting.called-timeout-scan-cron:0 * * * * *}")
  public void scanAndExpireCalled() {
    /* 분산락 획득 — 실패 시 다른 Worker Pod 가 처리 중이므로 즉시 종료 */
    Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, podId, LOCK_TTL);
    if (!Boolean.TRUE.equals(acquired)) {
      log.debug("Skipped — lock held by another worker pod.");
      return;
    }

    try {
      doScan();
    } finally {
      releaseLockIfOwner();
    }
  }

  private void doScan() {
    List<Long> sessionIds = waitingRedis.listActiveSessionIds();
    if (sessionIds.isEmpty()) return;

    long threshold = System.currentTimeMillis() - (callTimeoutMinutes * 60_000L);
    int expired = 0;

    for (Long sessionId : sessionIds) {
      List<String> tokens = waitingRedis.listActiveTokens(sessionId);
      for (String token : tokens) {
        WaitingTokenData data = waitingRedis.findByToken(token);
        if (data == null) continue;
        if (data.status() != WaitingStatus.CALLED) continue;
        if (data.calledAt() == null || data.calledAt() > threshold) continue;

        try {
          waitingRedis.cancelByOwner(token, sessionId, data.userId());
          expired++;
          log.info("Auto-cancelled timed-out CALLED. token={}", token);
        } catch (Exception e) {
          log.warn("Failed to auto-cancel token={}: {}", token, e.getMessage());
        }
      }
    }

    if (expired > 0) {
      log.info("CALLED timeout scan complete. expired={}", expired);
    }
  }

  /*
   * 락 해제 — 내가 소유한 락만 해제 (TTL 만료 후 다른 Pod 가 잡은 락을 건드리지 않기 위함).
   * GET → 비교 → DEL 사이 race 가능하지만 최악의 경우 다음 분에 한 번 더 스캔될 뿐이라 무해.
   */
  private void releaseLockIfOwner() {
    try {
      String owner = redis.opsForValue().get(LOCK_KEY);
      if (podId.equals(owner)) {
        redis.delete(LOCK_KEY);
      }
    } catch (Exception e) {
      log.warn("Failed to release lock — will expire by TTL.", e);
    }
  }
}
