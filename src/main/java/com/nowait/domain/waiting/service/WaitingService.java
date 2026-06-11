package com.nowait.domain.waiting.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.notification.service.NotificationService;
import com.nowait.domain.notification.type.NotificationType;
import com.nowait.domain.owner.repository.RestaurantOwnerRepository;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.entity.RestaurantHour;
import com.nowait.domain.restaurant.repository.RestaurantHourRepository;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.restaurant.type.DayOfWeek;
import com.nowait.domain.restaurant.type.RestaurantStatus;
import com.nowait.domain.waiting.dto.WaitingCallLogResponse;
import com.nowait.domain.waiting.dto.WaitingRegisterRequest;
import com.nowait.domain.waiting.dto.WaitingResponse;
import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.entity.WaitingCallLog;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.redis.WaitingTokenData;
import com.nowait.domain.waiting.repository.WaitingCallLogRepository;
import com.nowait.domain.waiting.repository.WaitingRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
  private final WaitingRepository waitingRepository;
  private final WaitingCallLogRepository waitingCallLogRepository;
  private final WaitingRedisLuaExecutor waitingRedis;
  private final NotificationService notificationService;
  private final RestaurantHourRepository restaurantHourRepository;

  /* ================== 사용자 ================== */

  /*
   * 사용자: 웨이팅 등록
   * POST /api/restaurants/{restaurantId}/waitings
   */
  @Transactional
  public WaitingResponse register(Long restaurantId, Long loginUserId,
      WaitingRegisterRequest request) {

    Restaurant restaurant = restaurantRepository.findById(restaurantId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

    if (restaurant.getStatus() != RestaurantStatus.OPEN) {
      throw new BusinessException(ErrorCode.RESTAURANT_NOT_OPEN);
    }
    if ("N".equals(restaurant.getWaitingAvailable())) {
      throw new BusinessException(ErrorCode.WAITING_NOT_AVAILABLE);
    }
    
    // A. 현재 '지금 이 순간'의 요일을 구하고, 3글자 Enum으로 변환
    String currentDayName = LocalDateTime.now().getDayOfWeek().name().substring(0, 3);
    DayOfWeek customDayOfWeek = DayOfWeek.valueOf(currentDayName);

    // B. DB에서 오늘 요일에 해당하는 식당의 영업 장부 조회
    RestaurantHour todayHourInfo = restaurantHourRepository
        .findByRestaurantIdAndDayOfWeek(restaurantId, customDayOfWeek)
        .orElseThrow(() -> new BusinessException(ErrorCode.OPERATING_INFO_NOT_FOUND));

    // C. 🚫 오늘이 정기 휴무일('Y')인지 검증
    if ("Y".equals(todayHourInfo.getIsRegularHoliday())) {
        throw new BusinessException(ErrorCode.RESTAURANT_CLOSED_DAY);
    }

    // D. 🚫 지금 줄 서는 시각이 영업시간 범위 내에 있는지 검증
    LocalTime currentTime = LocalTime.now();
    if (currentTime.isBefore(todayHourInfo.getOpenTime()) || currentTime.isAfter(todayHourInfo.getCloseTime())) {
    	throw new BusinessException(ErrorCode.NOT_OPERATING_TIME);
    }

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
    if (data == null) {
      log.error("Waiting hash missing from Redis immediately after registration. token={}", result.token());
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    return WaitingResponse.of(result.token(), data, 0L);
  }

  /*
   * 사용자: 내 웨이팅 전체 이력 조회 (활성 + DB 저장분)
   * GET /api/v1/waitings/me/history
   *
   * 활성 웨이팅(Redis)을 맨 앞에 두고, Worker 가 DB 에 sync 한 이력을 뒤에 붙인다.
   * Worker 가 꺼져 있으면 DB 부분은 비어 있을 수 있다.
   */
  public List<WaitingResponse> getMyWaitingHistory(Long loginUserId) {
    String activeToken = waitingRedis.findActiveTokenOf(loginUserId);
    List<WaitingResponse> result = new ArrayList<>();

    // 현재 Redis 에 있는 활성 웨이팅 (있으면 맨 앞에)
    if (activeToken != null) {
      WaitingTokenData data = waitingRedis.findByToken(activeToken);
      if (data != null) {
        long ahead = waitingRedis.aheadCount(data.sessionId(), activeToken);
        result.add(WaitingResponse.of(activeToken, data, ahead));
      }
    }

    // DB 이력 (Worker sync 분) — 활성 토큰과 중복 제거
    waitingRepository.findByUserIdOrderByRegisteredAtDesc(loginUserId)
        .stream()
        .filter(w -> activeToken == null || !activeToken.equals(w.getWaitingToken()))
        .map(WaitingResponse::from)
        .forEach(result::add);

    return result;
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
  @Transactional
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
  @Transactional
  public WaitingResponse call(String token, Long loginUserId) {
    WaitingTokenData data = findTokenDataOrThrow(token);
    verifyOwnership(data.restaurantId(), loginUserId);

    waitingRedis.call(token);

    /* 호출 로그 INSERT — DB 행 존재 시에만 (Worker 가 아직 sync 안 했을 수 있음) */
    waitingRepository.findByWaitingToken(token).ifPresent(waiting -> {
      LocalDateTime now = LocalDateTime.now();
      int sequence = waitingCallLogRepository.countByWaiting(waiting) + 1;
      waitingCallLogRepository.save(
          WaitingCallLog.builder()
              .waiting(waiting)
              .callSequence(sequence)
              .calledAt(now)
              .build()
      );
    });

    /* 호출 알림 */
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
  @Transactional
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
  @Transactional
  public WaitingResponse markEntered(String token, Long loginUserId) {
    WaitingTokenData data = findTokenDataOrThrow(token);
    verifyOwnership(data.restaurantId(), loginUserId);

    waitingRedis.enter(token, data.sessionId(), data.userId());
    log.info("Waiting entered. token={}", token);

    notifyNearCallIfThreshold(data.sessionId());

    WaitingTokenData updated = waitingRedis.findByToken(token);
    return WaitingResponse.of(token, updated == null ? data : updated);
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


  /* 점주: 호출 이력 조회 */
  public List<WaitingCallLogResponse> getCallLogs(Long waitingId, Long loginUserId) {
    Waiting waiting = findWaitingOrThrow(waitingId);
    verifyOwnership(waiting.getRestaurantId(), loginUserId);

    List<WaitingCallLog> logs =
        waitingCallLogRepository.findAllByWaitingOrderByCallSequenceAsc(waiting);

    return logs.stream()
        .map(WaitingCallLogResponse::new)
        .toList();
  }

  private Waiting findWaitingOrThrow(Long waitingId) {
    return waitingRepository.findById(waitingId)
        .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
  }
}
