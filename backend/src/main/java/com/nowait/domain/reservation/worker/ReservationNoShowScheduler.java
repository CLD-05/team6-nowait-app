package com.nowait.domain.reservation.worker;

import com.nowait.domain.reservation.redis.ReservationRedisLuaExecutor;
import com.nowait.domain.reservation.redis.ReservationTokenData;
import com.nowait.domain.reservation.type.ReservationStatus;
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
 * 노쇼 스케줄러 — 예약시각 + grace 가 지난 CONFIRMED 토큰을 NO_SHOW 처리.
 *
 * 동작:
 *   - 주기적으로 reservation:noshow-candidates ZSET 을 ZRANGEBYSCORE 0 (now - grace) 스캔
 *   - 각 토큰 상태가 CONFIRMED 면 no-show Lua 호출 (Lua 가 자동으로 pending-sync 푸시)
 *
 * 다중 Worker Pod 안전성:
 *   - Redis SETNX 분산락으로 한 번에 한 Pod 만 실행
 *   - 락 TTL 50초 (스캔이 멈춰도 자동 해제)
 */
@Slf4j
@Component
@Profile("reservation-worker")
@RequiredArgsConstructor
public class ReservationNoShowScheduler {

  private static final String LOCK_KEY = "reservation:scheduler:lock:no-show";
  private static final Duration LOCK_TTL = Duration.ofSeconds(50);

  /* Pod 식별자 — TTL 만료 후 다른 Pod 락 실수 해제 방지 */
  private final String podId = UUID.randomUUID().toString();

  private final ReservationRedisLuaExecutor reservationRedis;
  private final StringRedisTemplate redis;

  @Value("${reservation.no-show-grace-minutes:30}")
  private long graceMinutes;

  @Scheduled(cron = "${worker.reservation.noshow-scan-cron:0 */5 * * * *}")
  public void scanAndMarkNoShow() {
    Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, podId, LOCK_TTL);
    if (!Boolean.TRUE.equals(acquired)) {
      log.debug("Skipped — no-show lock held by another worker pod.");
      return;
    }

    try {
      doScan();
    } finally {
      releaseLockIfOwner();
    }
  }

  private void doScan() {
    long threshold = System.currentTimeMillis() - (graceMinutes * 60_000L);
    List<String> tokens = reservationRedis.listNoShowCandidates(threshold);
    if (tokens.isEmpty()) return;

    int marked = 0;
    for (String token : tokens) {
      ReservationTokenData data = reservationRedis.findByToken(token);
      if (data == null) continue;
      if (data.status() != ReservationStatus.CONFIRMED) continue;

      try {
        reservationRedis.noShow(token, data.userId(), data.slotId());
        marked++;
        log.info("Auto-marked NO_SHOW. token={}", token);
      } catch (Exception e) {
        log.warn("Failed to auto-mark NO_SHOW. token={} reason={}", token, e.getMessage());
      }
    }

    if (marked > 0) {
      log.info("NO_SHOW scan complete. marked={}", marked);
    }
  }

  /* 락 해제 — 내가 소유한 락만 해제 */
  private void releaseLockIfOwner() {
    try {
      String owner = redis.opsForValue().get(LOCK_KEY);
      if (podId.equals(owner)) {
        redis.delete(LOCK_KEY);
      }
    } catch (Exception e) {
      log.warn("Failed to release no-show lock — will expire by TTL.", e);
    }
  }
}
