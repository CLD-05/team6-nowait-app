package com.nowait.domain.waiting.service;

import com.nowait.domain.notification.service.NotificationService;
import com.nowait.domain.notification.type.NotificationType;
import com.nowait.domain.owner.repository.RestaurantOwnerRepository;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.waiting.dto.WaitingRegisterRequest;
import com.nowait.domain.waiting.dto.WaitingResponse;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.redis.WaitingTokenData;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/*
 * 웨이팅 서비스 — Redis-first 아키텍처.
 *
 * 동작 원리:
 *   1. 등록/취소/호출/입장 모두 Redis Lua 로 원자적 처리
 *   2. RDS 저장은 Worker 가 비동기로 수행 (waiting:pending-sync 큐 소비)
 *   3. 본 서비스는 DB INSERT/UPDATE 를 하지 않음 (세션 entity 의 status 변경만 예외)
 *
 * 식별자:
 *   - 사용자/점주 모두 waitingToken (UUID) 으로 후속 액션 호출
 *   - waitingId 는 Worker 가 RDS INSERT 한 후에야 존재 → API 흐름에서는 사용 X
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingService {

  private static final int NEAR_CALL_THRESHOLD = 5;

  private final WaitingSessionService waitingSessionService;
  private final RestaurantOwnerRepository restaurantOwnerRepository;
  private final RestaurantRepository restaurantRepository;
  private final WaitingRedisLuaExecutor waitingRedis;
  private final NotificationService notificationService;

  /* ================== 사용자 ================== */

  /*
   * 사용자: 웨이팅 등록
   * POST /api/restaurants/{restaurantId}/waitings
   */
  public WaitingResponse register(Long restaurantId, Long loginUserId,
      WaitingRegisterRequest request) {
    WaitingSession session = waitingSessionService.findSessionOrThrow(
        findTodaySessionId(restaurantId));

    if (!session.getStatus().canAcceptWaiting()) {
      throw new BusinessException(ErrorCode.WAITING_SESSION_NOT_ACCEPTING);
    }

    /* Lua 가 6개 키 원자 처리 — 중복/한도/채번/큐/Hash/사용자맵/Worker큐 */
    WaitingRedisLuaExecutor.RegisterResult result = waitingRedis.register(
        loginUserId,
        session.getId(),
        restaurantId,
        request.partySize(),
        session.getMaxWaitingCount()
    );

    log.info("Waiting registered. token={}, sessionId={}, number={}, userId={}",
        result.token(), session.getId(), result.waitingNumber(), loginUserId);

    /* 5팀 알림 트리거 (신규 등록자가 6번째 자리이면) */
    notifyNearCallIfThreshold(session.getId());

    WaitingTokenData data = waitingRedis.findByToken(result.token());
    return WaitingResponse.of(result.token(), data, 0L);
  }

  /*
   * 사용자: 내 활성 웨이팅 조회
   * GET /api/waitings/me
   */
  public WaitingResponse getMyWaiting(Long loginUserId) {
    String token = waitingRedis.findActiveTokenOf(loginUserId);
    if (token == null) {
      throw new BusinessException(ErrorCode.WAITING_NOT_FOUND);
    }
    WaitingTokenData data = waitingRedis.findByToken(token);
    if (data == null) {
      throw new BusinessException(ErrorCode.WAITING_NOT_FOUND);
    }
    long ahead = waitingRedis.aheadCount(data.sessionId(), token);
    return WaitingResponse.of(token, data, ahead);
  }

  /*
   * 사용자: 본인 웨이팅 취소
   * PATCH /api/waitings/{token}/cancel
   */
  public WaitingResponse cancelByUser(String token, Long loginUserId) {
    WaitingTokenData data = findTokenDataOrThrow(token);

    waitingRedis.cancel(token, loginUserId, data.sessionId());
    log.info("Waiting cancelled by user. token={}, userId={}", token, loginUserId);

    notifyNearCallIfThreshold(data.sessionId());

    WaitingTokenData updated = waitingRedis.findByToken(token);
    return WaitingResponse.of(token, updated == null ? data : updated);
  }

  /* ================== 점주 ================== */

  /*
   * 점주: 세션의 활성 웨이팅 목록 조회
   * GET /api/owners/restaurants/{restaurantId}/waitings
   */
  public List<WaitingResponse> getOwnerWaitings(Long restaurantId, Long loginUserId) {
    verifyOwnership(restaurantId, loginUserId);

    Long sessionId = findTodaySessionId(restaurantId);
    List<String> tokens = waitingRedis.listActiveTokens(sessionId);

    return tokens.stream()
        .map(token -> {
          WaitingTokenData data = waitingRedis.findByToken(token);
          return data == null ? null : WaitingResponse.of(token, data);
        })
        .filter(Objects::nonNull)
        .toList();
  }

  /*
   * 점주: 호출 (WAITING → CALLED)
   * PATCH /api/owners/waiting/{token}/call
   */
  public WaitingResponse call(String token, Long loginUserId) {
    WaitingTokenData data = findTokenDataOrThrow(token);
    verifyOwnership(data.restaurantId(), loginUserId);

    waitingRedis.call(token);

    /* 호출 알림 (트랜잭션 격리 없이 그대로 — Redis 라 트랜잭션 개념 없음) */
    String restaurantName = getRestaurantName(data.restaurantId());
    notificationService.notify(
        data.userId(),
        NotificationType.WAITING_CALLED,
        restaurantName + " 입장해주세요!"
    );

    log.info("Waiting called. token={}", token);

    WaitingTokenData updated = waitingRedis.findByToken(token);
    return WaitingResponse.of(token, updated == null ? data : updated);
  }

  /*
   * 점주: 취소 처리 (WAITING/CALLED → CANCELLED)
   * PATCH /api/owners/waiting/{token}/cancelled
   */
  public WaitingResponse cancelByOwner(String token, Long loginUserId) {
    WaitingTokenData data = findTokenDataOrThrow(token);
    verifyOwnership(data.restaurantId(), loginUserId);

    waitingRedis.cancelByOwner(token, data.sessionId(), data.userId());
    log.info("Waiting cancelled by owner. token={}", token);

    notifyNearCallIfThreshold(data.sessionId());

    WaitingTokenData updated = waitingRedis.findByToken(token);
    return WaitingResponse.of(token, updated == null ? data : updated);
  }

  /*
   * 점주: 입장 처리 (CALLED → ENTERED)
   * PATCH /api/owners/waiting/{token}/enter
   * PATCH /api/owners/waiting/{token}/entered
   */
  public WaitingResponse markEntered(String token, Long loginUserId) {
    WaitingTokenData data = findTokenDataOrThrow(token);
    verifyOwnership(data.restaurantId(), loginUserId);

    waitingRedis.enter(token, data.sessionId(), data.userId());
    log.info("Waiting entered. token={}", token);

    notifyNearCallIfThreshold(data.sessionId());

    WaitingTokenData updated = waitingRedis.findByToken(token);
    return WaitingResponse.of(token, updated == null ? data : updated);
  }

  /* ================== 스케줄러 ================== */

  /*
   * 스케줄러: CALLED 후 타임아웃 자동 취소
   *
   * 현재 Phase 2 에서는 stub. Phase 3 (Worker) 에서 Redis 기반으로 재구현 예정.
   * (모든 활성 세션의 CALLED 토큰을 calledAt 기준으로 스캔해야 하므로
   *  비용이 큰 작업 — Worker 의 주기적 작업에 통합하는 것이 자연스러움.)
   */
  public int cancelExpiredCalls() {
    // TODO: Phase 3 에서 Redis 스캔 기반으로 구현
    return 0;
  }

  // ================== 내부 헬퍼 ==================

  private WaitingTokenData findTokenDataOrThrow(String token) {
    WaitingTokenData data = waitingRedis.findByToken(token);
    if (data == null) {
      throw new BusinessException(ErrorCode.WAITING_NOT_FOUND);
    }
    return data;
  }

  private Long findTodaySessionId(Long restaurantId) {
    return waitingSessionService.getTodaySession(restaurantId).sessionId();
  }

  private void verifyOwnership(Long restaurantId, Long loginUserId) {
    if (!restaurantOwnerRepository.existsByUserIdAndRestaurantId(loginUserId, restaurantId)) {
      throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
    }
  }

  /* 앞 5팀 알림 — 활성 큐의 인덱스 5(=6번째)가 미알림이면 1회 알림. 본 작업 실패 격리. */
  private void notifyNearCallIfThreshold(Long sessionId) {
    try {
      List<String> tokens = waitingRedis.listActiveTokens(sessionId);
      if (tokens.size() <= NEAR_CALL_THRESHOLD) return;

      String candidateToken = tokens.get(NEAR_CALL_THRESHOLD);
      boolean justMarked = waitingRedis.markNearCallNotifiedIfAbsent(candidateToken);
      if (!justMarked) return;

      WaitingTokenData data = waitingRedis.findByToken(candidateToken);
      if (data == null) return;

      String restaurantName = getRestaurantName(data.restaurantId());
      notificationService.notify(
          data.userId(),
          NotificationType.WAITING_NEAR_CALL,
          restaurantName + " 입장까지 5팀 남았어요!"
      );
    } catch (Exception e) {
      log.error("Failed to send near-call notification. sessionId={}", sessionId, e);
    }
  }

  private String getRestaurantName(Long restaurantId) {
    return restaurantRepository.findById(restaurantId)
        .map(Restaurant::getName)
        .orElse("매장");
  }
}
