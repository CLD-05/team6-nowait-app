package com.nowait.domain.waiting.service;

import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.redis.WaitingRedisKeys;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.redis.WaitingTokenData;
import com.nowait.domain.waiting.repository.WaitingRepository;
import com.nowait.domain.waiting.repository.WaitingSessionRepository;
import com.nowait.domain.waiting.type.WaitingSessionStatus;
import com.nowait.domain.waiting.type.WaitingStatus;
import com.nowait.global.common.TimeZones;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * 오늘 이전(전날까지) 날짜의 미마감 웨이팅 세션을 정리(마감)한다.
 *
 * 배경:
 *   WaitingSessionDailyOpenScheduler 는 매일 새 세션을 OPEN 하지만 이전 날짜 세션을
 *   CLOSED 로 내리는 로직이 없어, 한 식당에 여러 날짜의 OPEN 세션이 동시에 누적됐다.
 *   "순번 확인"은 (restaurant_id, 오늘 날짜)로 세션을 조회하므로, 과거 세션에 남은 웨이팅은
 *   조회되지 않아 "웨이팅 정보를 찾을 수 없어요"가 발생했다.
 *
 * 트랜잭션 경계를 스케줄러가 아닌 이 서비스 빈에 둔다. 스케줄러에서 같은 빈의
 * @Transactional 메서드를 호출하면 self-invocation 으로 프록시가 적용되지 않아
 * 엔티티 dirty-checking 이 flush 되지 않기 때문이다. 세션 1건당 독립 트랜잭션으로
 * 처리해 한 세션 실패가 다른 세션 마감을 막지 않도록 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingSessionCloseService {

  private static final List<WaitingSessionStatus> CLOSABLE =
      List.of(WaitingSessionStatus.OPEN, WaitingSessionStatus.PAUSED);
  private static final List<WaitingStatus> ACTIVE =
      List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);

  private final WaitingSessionRepository waitingSessionRepository;
  private final WaitingRepository waitingRepository;
  private final WaitingRedisLuaExecutor waitingRedis;
  private final StringRedisTemplate redis;

  /* 오늘 이전(전날까지)의 미마감(OPEN/PAUSED) 세션 ID 목록 */
  @Transactional(readOnly = true)
  public List<Long> findStaleSessionIds(LocalDate today) {
    return waitingSessionRepository
        .findByStatusInAndSessionDateBefore(CLOSABLE, today)
        .stream()
        .map(WaitingSession::getId)
        .toList();
  }

  /*
   * 세션 1건 마감 + 남은 웨이팅 취소 + Redis 잔재 정리.
   * 멱등: 이미 CLOSED 면 즉시 반환. Redis/DB 어느 한쪽에만 남은 잔재도 양방향으로 정리한다.
   *
   * @return 이번 호출에서 취소 처리한 웨이팅 건수
   */
  @Transactional
  public int closeSession(Long sessionId) {
    WaitingSession session = waitingSessionRepository.findById(sessionId).orElse(null);
    if (session == null || session.getStatus().isClosed()) {
      return 0;
    }
    LocalDateTime now = LocalDateTime.now(TimeZones.KST);

    // 1) Redis 큐에 남은 토큰을 점주 강제취소와 동일 경로로 취소한다.
    //    cancelByOwner: 큐 제거 + count 감소 + userActive 삭제 + token 상태 CANCELLED 마킹
    //    + pending-sync 적재(→ WaitingSyncWorker 가 DB 에 CANCELLED 반영).
    Set<String> handledTokens = new HashSet<>();
    for (String token : waitingRedis.listActiveTokens(sessionId)) {
      WaitingTokenData data = waitingRedis.findByToken(token);
      if (data == null) {
        continue;
      }
      try {
        waitingRedis.cancelByOwner(token, sessionId, data.userId(), data.restaurantId());
        handledTokens.add(token);
      } catch (Exception e) {
        log.warn("DailyClose: Redis 취소 실패 sessionId={}, token={}: {}",
            sessionId, token, e.getMessage());
      }
    }

    // 2) DB 안전망 — 활성으로 남은 행을 직접 취소(즉시 DB 정합성 확보, 비동기 동기화에 의존하지 않음).
    //    Redis 경로로 처리되지 않은 진짜 orphan 만 Redis 잔재(token Hash / userActive)를 정리한다.
    int cancelled = 0;
    for (Waiting w : waitingRepository.findBySessionIdAndStatusInOrderByWaitingNumberAsc(sessionId, ACTIVE)) {
      if (!w.getStatus().isTerminal()) {
        w.cancel(now);
        cancelled++;
      }
      if (handledTokens.contains(w.getWaitingToken())) {
        // 이미 cancelByOwner 가 정리했고, token Hash 는 WaitingSyncWorker 가 읽어야 하므로 보존
        continue;
      }
      redis.delete(WaitingRedisKeys.token(w.getWaitingToken()));
      // 같은 (user, restaurant)로 오늘 새로 등록한 토큰을 지우지 않도록, 이 웨이팅의 토큰일 때만 삭제
      String userActiveKey = WaitingRedisKeys.userActive(w.getUserId(), w.getRestaurantId());
      if (w.getWaitingToken().equals(redis.opsForValue().get(userActiveKey))) {
        redis.delete(userActiveKey);
      }
    }

    // 3) 세션 마감 (OPEN/PAUSED → CLOSED)
    session.close(now);

    // 4) 세션 단위 Redis 키 정리 + active-sessions 해제 (queue/count/next-number 삭제)
    waitingRedis.clearSession(sessionId);

    log.info("DailyClose: 세션 마감 sessionId={}, restaurantId={}, date={}, cancelledWaitings={}",
        sessionId, session.getRestaurantId(), session.getSessionDate(), cancelled);
    return cancelled;
  }
}
