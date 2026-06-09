package com.nowait.domain.waiting.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nowait.domain.notification.service.NotificationService;
import com.nowait.domain.notification.type.NotificationType;
import com.nowait.domain.owner.repository.RestaurantOwnerRepository;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.waiting.dto.WaitingCallLogResponse;
import com.nowait.domain.waiting.dto.WaitingRegisterRequest;
import com.nowait.domain.waiting.dto.WaitingResponse;
import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.entity.WaitingCallLog;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.repository.WaitingCallLogRepository;
import com.nowait.domain.waiting.repository.WaitingRedisRepository;
import com.nowait.domain.waiting.repository.WaitingRepository;
import com.nowait.domain.waiting.type.WaitingStatus;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingService {

  private static final Set<WaitingStatus> ACTIVE_STATUSES = Set.of(WaitingStatus.WAITING, WaitingStatus.CALLED);
  private final WaitingCallLogRepository waitingCallLogRepository;
  private static final int NEAR_CALL_THRESHOLD = 5;

  private final WaitingRepository waitingRepository;
  private final WaitingSessionService waitingSessionService;
  private final RestaurantOwnerRepository restaurantOwnerRepository;
  private final WaitingRedisRepository waitingRedisRepository;
  private final NotificationService notificationService;
  private final RestaurantRepository restaurantRepository;

  @Value("${waiting.call-timeout-minutes:10}")
  private long callTimeoutMinutes;

  /*
   * 사용자: 웨이팅 등록
   * POST /api/restaurants/{restaurantId}/waitings
   */
  @Transactional
  public WaitingResponse register(Long restaurantId, Long loginUserId,
      WaitingRegisterRequest request) {
    WaitingSession session = waitingSessionService
        .findSessionOrThrow(findTodaySessionId(restaurantId));

    if (!session.getStatus().canAcceptWaiting()) {
      throw new BusinessException(ErrorCode.WAITING_SESSION_NOT_ACCEPTING);
    }
    if (waitingRepository.existsByUserIdAndSessionIdAndStatusIn(
        loginUserId, session.getId(), ACTIVE_STATUSES)) {
      throw new BusinessException(ErrorCode.DUPLICATE_WAITING);
    }

    // Redis 로 카운트 사전 확인 + 증가 (atomic)
    int newCount = waitingRedisRepository.incrementCount(session.getId());
    if (newCount > session.getMaxWaitingCount()) {
      // 한도 초과 → 롤백 (감소)
      waitingRedisRepository.decrementCount(session.getId());
      throw new BusinessException(ErrorCode.WAITING_COUNT_EXCEEDED);
    }

    // Redis 로 대기번호 atomic 채번
    int nextNumber = waitingRedisRepository.incrementAndGetNextNumber(session.getId());

    // DB 반영 (엔티티의 currentCount 동기화)
    session.increaseCurrentCount();
    Waiting waiting = Waiting.register(
        loginUserId, restaurantId, session.getId(),
        nextNumber, request.partySize(), LocalDateTime.now());
    waitingRepository.save(waiting);

    log.info("Waiting registered. waitingId={}, sessionId={}, number={}, userId={}, redisCount={}",
        waiting.getId(), session.getId(), nextNumber, loginUserId, newCount);

    // 신규 등록자가 6번째 자리이면 앞 5팀 알림 발송
    notifyNearCallIfThreshold(session.getId());

    return WaitingResponse.of(waiting, 0L);
  }

  /*
   * 사용자: 내 활성 웨이팅 조회
   * GET /api/waitings/me
   */
  public WaitingResponse getMyWaiting(Long loginUserId) {
    Waiting waiting = waitingRepository
        .findFirstByUserIdAndStatusInOrderByRegisteredAtDesc(loginUserId, ACTIVE_STATUSES)
        .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));

    long aheadCount = waitingRepository.countAhead(
        waiting.getSessionId(), ACTIVE_STATUSES, waiting.getWaitingNumber());

    return WaitingResponse.of(waiting, aheadCount);
  }

  /*
   * 사용자: 본인 웨이팅 취소
   * PATCH /api/waitings/{waitingId}/cancel
   */
  @Transactional
  public WaitingResponse cancelByUser(Long waitingId, Long loginUserId) {
    Waiting waiting = findWaitingOrThrow(waitingId);
    if (!waiting.isOwnedBy(loginUserId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
    applyCancel(waiting);
    log.info("Waiting cancelled by user. waitingId={}, userId={}", waitingId, loginUserId);
    return WaitingResponse.from(waiting);
  }

  /*
   * 점주: 세션의 웨이팅 목록 조회
   * GET /api/owners/restaurants/{restaurantId}/waitings
   */
  public List<WaitingResponse> getOwnerWaitings(Long restaurantId, Long loginUserId) {
    verifyOwnership(restaurantId, loginUserId);

    Long sessionId = findTodaySessionId(restaurantId);
    return waitingRepository.findBySessionIdOrderByWaitingNumberAsc(sessionId).stream()
        .map(WaitingResponse::from)
        .toList();
  }

  /*
   * 점주: 호출 (WAITING → CALLED)
   * PATCH /api/owners/waiting/{waitingId}/call
   */
  @Transactional
  public WaitingResponse call(Long waitingId, Long loginUserId) {
    Waiting waiting = findWaitingOrThrow(waitingId);
    verifyOwnership(waiting.getRestaurantId(), loginUserId);
    
    LocalDateTime now = LocalDateTime.now();
    int currentCallCount = waitingCallLogRepository.countByWaiting(waiting);
    
    WaitingCallLog logEntity = WaitingCallLog.builder()
    		.waiting(waiting)
    		.callSequence(currentCallCount + 1)
    		.calledAt(now)
    		.build();
    waitingCallLogRepository.save(logEntity);
    
    waiting.call(now);

    // 점주 호출 알림 (트랜잭션 묶음 — 실패 시 호출도 롤백)
    String restaurantName = getRestaurantName(waiting.getRestaurantId());
    notificationService.notify(
        waiting.getUserId(),
        NotificationType.WAITING_CALLED,
        restaurantName + " 입장해주세요!"
    );

    log.info("Waiting called. waitingId={}", waitingId);
    return WaitingResponse.from(waiting);
  }

  /*
   * 점주: 취소 처리 (WAITING/CALLED → CANCELLED)
   * PATCH /api/owners/waiting/{waitingId}/cancelled
   */
  @Transactional
  public WaitingResponse cancelByOwner(Long waitingId, Long loginUserId) {
    Waiting waiting = findWaitingOrThrow(waitingId);
    verifyOwnership(waiting.getRestaurantId(), loginUserId);
    applyCancel(waiting);
    log.info("Waiting cancelled by owner. waitingId={}", waitingId);
    return WaitingResponse.from(waiting);
  }

  /*
   * 점주: 입장 처리 (CALLED → ENTERED)
   * PATCH /api/owners/waiting/{waitingId}/enter
   * PATCH /api/owners/waiting/{waitingId}/entered
   * ※ 명세상 두 엔드포인트가 별도 존재하나, 현재 DDL 상 동일한 상태 전이.
   * 실질적 차이가 생기면 분기, 지금은 같은 메서드 호출.
   */
  @Transactional
  public WaitingResponse markEntered(Long waitingId, Long loginUserId) {
    Waiting waiting = findWaitingOrThrow(waitingId);
    verifyOwnership(waiting.getRestaurantId(), loginUserId);

    WaitingSession session = waitingSessionService.findSessionOrThrow(waiting.getSessionId());
    waiting.enter(LocalDateTime.now());
    session.decreaseCurrentCount();
    waitingRedisRepository.decrementCount(session.getId()); // Redis 카운트도 감소

    // 입장으로 한 자리 비었으니 새 6번째 후보에게 앞 5팀 알림 발송
    notifyNearCallIfThreshold(waiting.getSessionId());

    log.info("Waiting entered. waitingId={}", waitingId);
    return WaitingResponse.from(waiting);
  }

  /*
   * 스케줄러: CALLED 후 타임아웃된 웨이팅 자동 취소
   * 호출자: WaitingTimeoutScheduler
   */
  @Transactional
  public int cancelExpiredCalls() {
    LocalDateTime threshold = LocalDateTime.now().minusMinutes(callTimeoutMinutes);
    List<Waiting> expired = waitingRepository.findByStatusAndCalledAtBefore(
        WaitingStatus.CALLED, threshold);

    if (expired.isEmpty())
      return 0;

    for (Waiting w : expired) {
      applyCancel(w);
    }

    // 영향받은 세션마다 한 번씩 앞 5팀 알림 체크
    expired.stream()
        .map(Waiting::getSessionId)
        .distinct()
        .forEach(this::notifyNearCallIfThreshold);

    log.info("Auto-cancelled {} waitings due to timeout (>{}min)", expired.size(), callTimeoutMinutes);
    return expired.size();
  }

  /* 공통 취소 로직: 상태 전이 + 세션 카운트 감소 */
  private void applyCancel(Waiting waiting) {
    WaitingSession session = waitingSessionService.findSessionOrThrow(waiting.getSessionId());
    waiting.cancel(LocalDateTime.now());
    session.decreaseCurrentCount();
    waitingRedisRepository.decrementCount(session.getId()); // Redis 카운트도 감소

    // 취소로 자리가 비었으니 새 6번째 후보에게 앞 5팀 알림 발송
    notifyNearCallIfThreshold(waiting.getSessionId());
  }

  /* 앞 5팀 도달 시 1회 알림 (이미 받은 사람은 스킵)
   * - 본 작업(취소/입장 등)에 영향 안 주도록 try-catch 격리
   */
  private void notifyNearCallIfThreshold(Long sessionId) {
    try {
      List<Waiting> active = waitingRepository
          .findBySessionIdAndStatusInOrderByWaitingNumberAsc(sessionId, ACTIVE_STATUSES);

      if (active.size() <= NEAR_CALL_THRESHOLD) return;

      Waiting candidate = active.get(NEAR_CALL_THRESHOLD); // index 5 = 앞에 5팀
      if (candidate.isNearCallNotified()) return;

      String restaurantName = getRestaurantName(candidate.getRestaurantId());
      notificationService.notify(
          candidate.getUserId(),
          NotificationType.WAITING_NEAR_CALL,
          restaurantName + " 입장까지 5팀 남았어요!"
      );
      candidate.markNearCallNotified();
    } catch (Exception e) {
      log.error("Failed to send near-call notification. sessionId={}", sessionId, e);
    }
  }

  private String getRestaurantName(Long restaurantId) {
    return restaurantRepository.findById(restaurantId)
        .map(Restaurant::getName)
        .orElse("매장");
  }

  private Waiting findWaitingOrThrow(Long waitingId) {
    return waitingRepository.findById(waitingId)
        .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_NOT_FOUND));
  }

  private Long findTodaySessionId(Long restaurantId) {
    return waitingSessionService.getTodaySession(restaurantId).sessionId();
  }

  private void verifyOwnership(Long restaurantId, Long loginUserId) {
    if (!restaurantOwnerRepository.existsByUserIdAndRestaurantId(loginUserId, restaurantId)) {
      throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
    }
  }
  
  public List<WaitingCallLogResponse> getCallLogs(Long waitingId, Long loginUserId) {
	  
	  Waiting waiting = findWaitingOrThrow(waitingId);
	  
	  verifyOwnership(waiting.getRestaurantId(), loginUserId);
	  
	  List<WaitingCallLog> logs = waitingCallLogRepository.findAllByWaitingOrderByCallSequenceAsc(waiting);
	  
	  return logs.stream()
			  .map(WaitingCallLogResponse::new)
			  .toList();
  }
  
}