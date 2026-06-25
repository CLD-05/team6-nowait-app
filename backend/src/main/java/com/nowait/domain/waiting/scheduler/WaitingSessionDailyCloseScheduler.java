package com.nowait.domain.waiting.scheduler;

import com.nowait.domain.waiting.service.WaitingSessionCloseService;
import com.nowait.global.common.TimeZones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/*
 * 매일 자정(KST) 직후, 오늘 이전 날짜의 미마감 웨이팅 세션을 CLOSED 로 정리한다.
 *
 * 배경:
 *   WaitingSessionDailyOpenScheduler 는 매일 새 세션을 OPEN 하지만 이전 날짜 세션을
 *   닫지 않아 한 식당에 여러 날짜의 OPEN 세션이 누적됐다. "순번 확인"은 (식당, 오늘 날짜)
 *   세션만 보므로, 과거 세션에 남은 웨이팅은 "웨이팅 정보를 찾을 수 없어요"가 됐다.
 *   전날 세션을 닫고 남은 웨이팅을 취소 + Redis 잔재를 정리해 근본 원인을 제거한다.
 *
 * 실행 시각:
 *   Open 스케줄러(0 0 0)가 오늘 세션을 만든 뒤 실행되도록 기본 00:10(KST). 이 시점에
 *   "오늘 이전" 세션만 마감 대상이라 오늘 세션은 영향받지 않는다.
 *
 * 다중 Worker Pod 안전성:
 *   - 다른 waiting 스케줄러와 동일한 Redis SETNX 분산락으로 한 Pod 만 실행한다.
 *   - 세션 1건당 독립 트랜잭션(WaitingSessionCloseService#closeSession)으로 처리하며
 *     각 단계가 멱등이라, 락이 TTL 로 풀려 다른 Pod 가 겹쳐 실행해도 안전하다.
 */
@Slf4j
@Component
@Profile("waiting-worker")
@RequiredArgsConstructor
public class WaitingSessionDailyCloseScheduler {

  private static final String LOCK_KEY = "waiting:scheduler:lock:daily-close";
  private static final Duration LOCK_TTL = Duration.ofSeconds(50);

  /* Pod 식별자 — 락 소유자 검증용 (TTL 만료 후 다른 Pod 의 락을 실수로 해제하지 않기 위함) */
  private final String podId = UUID.randomUUID().toString();

  private final WaitingSessionCloseService closeService;
  private final StringRedisTemplate redis;

  @Scheduled(cron = "${worker.waiting.daily-close-cron:0 10 0 * * *}", zone = "Asia/Seoul")
  public void closePreviousDaySessions() {
    Boolean acquired = redis.opsForValue().setIfAbsent(LOCK_KEY, podId, LOCK_TTL);
    if (!Boolean.TRUE.equals(acquired)) {
      log.debug("Skipped — daily-close lock held by another worker pod.");
      return;
    }

    try {
      LocalDate today = LocalDate.now(TimeZones.KST);
      List<Long> staleSessionIds = closeService.findStaleSessionIds(today);
      if (staleSessionIds.isEmpty()) {
        log.info("WaitingSessionDailyCloseScheduler: 마감할 이전 세션 없음 (today={})", today);
        return;
      }

      int closed = 0;
      int cancelled = 0;
      for (Long sessionId : staleSessionIds) {
        try {
          cancelled += closeService.closeSession(sessionId);
          closed++;
        } catch (Exception e) {
          log.warn("WaitingSessionDailyCloseScheduler: 세션 마감 실패 sessionId={}: {}",
              sessionId, e.getMessage());
        }
      }
      log.info("WaitingSessionDailyCloseScheduler 완료: {}개 세션 마감, {}건 웨이팅 취소 (today={})",
          closed, cancelled, today);
    } finally {
      releaseLockIfOwner();
    }
  }

  /*
   * 락 해제 — 내가 소유한 락만 해제. GET → 비교 → DEL 사이 race 가 있으나 최악의 경우
   * 다음 실행에서 멱등하게 한 번 더 도는 것뿐이라 무해하다.
   */
  private void releaseLockIfOwner() {
    try {
      String owner = redis.opsForValue().get(LOCK_KEY);
      if (podId.equals(owner)) {
        redis.delete(LOCK_KEY);
      }
    } catch (Exception e) {
      log.warn("Failed to release daily-close lock — will expire by TTL.", e);
    }
  }
}
