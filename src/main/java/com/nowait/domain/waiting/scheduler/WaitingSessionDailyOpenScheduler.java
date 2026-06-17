package com.nowait.domain.waiting.scheduler;

import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.repository.WaitingSessionRepository;
import com.nowait.global.common.TimeZones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * WaitingSessionSeedRunner는 ApplicationRunner라서 파드가 부팅될 때 "오늘"의 세션만 1회 생성한다.
 * 파드가 자정(KST)을 넘겨 재시작 없이 계속 떠 있으면 새 날짜의 세션이 전혀 생성되지 않고,
 * 점주가 매일 직접 "세션 오픈"을 눌러야만 웨이팅 기능이 동작한다. 매일 자정 KST에 동일한
 * idempotent 로직을 다시 실행해 날짜 롤오버 문제를 근본적으로 막는다.
 *
 * 다중 Worker Pod 안전성:
 *   - waiting_sessions 테이블에는 (restaurant_id, session_date) unique 제약이 없으므로,
 *     모든 Pod가 정확히 같은 cron tick에 동시 실행되면 중복 행이 생길 수 있다.
 *   - 다른 waiting 스케줄러들과 동일한 Redis SETNX 분산락 패턴으로 한 Pod만 실행되게 한다.
 */
@Slf4j
@Component
@Profile("waiting-worker")
@RequiredArgsConstructor
public class WaitingSessionDailyOpenScheduler {

  private static final int DEFAULT_MAX_WAITING = 30;
  private static final String LOCK_KEY = "waiting:scheduler:lock:daily-open";
  private static final Duration LOCK_TTL = Duration.ofSeconds(50);

  /* Pod 식별자 — 락 소유자 검증용 */
  private final String podId = UUID.randomUUID().toString();

  private final RestaurantRepository restaurantRepository;
  private final WaitingSessionRepository waitingSessionRepository;
  private final WaitingRedisLuaExecutor waitingRedis;
  private final StringRedisTemplate redis;

  @Scheduled(cron = "${worker.waiting.daily-open-cron:0 0 0 * * *}", zone = "Asia/Seoul")
  public void openTodaySessions() {
    Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, podId, LOCK_TTL);
    if (!Boolean.TRUE.equals(acquired)) {
      log.debug("Skipped — daily-open lock held by another worker pod.");
      return;
    }

    try {
      doOpen();
    } finally {
      releaseLockIfOwner();
    }
  }

  @Transactional
  public void doOpen() {
    LocalDate today = LocalDate.now(TimeZones.KST);
    LocalDateTime now = LocalDateTime.now(TimeZones.KST);

    List<WaitingSession> toInit = new ArrayList<>();

    for (var restaurant : restaurantRepository.findAll()) {
      Long restaurantId = restaurant.getId();
      if (!waitingSessionRepository.existsByRestaurantIdAndSessionDate(restaurantId, today)) {
        WaitingSession session = WaitingSession.open(
            restaurantId, today, DEFAULT_MAX_WAITING, now);
        waitingSessionRepository.save(session);
        toInit.add(session);
      }
    }

    toInit.forEach(session -> waitingRedis.initSession(session.getId()));

    log.info("WaitingSessionDailyOpenScheduler 완료: {}개 세션 생성 (date={})", toInit.size(), today);
  }

  private void releaseLockIfOwner() {
    try {
      String owner = redis.opsForValue().get(LOCK_KEY);
      if (podId.equals(owner)) {
        redis.delete(LOCK_KEY);
      }
    } catch (Exception e) {
      log.warn("Failed to release daily-open lock — will expire by TTL.", e);
    }
  }
}
