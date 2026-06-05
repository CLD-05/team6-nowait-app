package com.nowait.domain.waiting.service;

import com.nowait.domain.owner.repository.RestaurantOwnerRepository;
import com.nowait.domain.waiting.dto.WaitingRegisterRequest;
import com.nowait.domain.waiting.dto.WaitingResponse;
import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.repository.WaitingRepository;
import com.nowait.domain.waiting.type.WaitingStatus;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingService {

  private static final Set<WaitingStatus> ACTIVE_STATUSES = Set.of(WaitingStatus.WAITING, WaitingStatus.CALLED);

  private final WaitingRepository waitingRepository;
  private final WaitingSessionService waitingSessionService;
  private final RestaurantOwnerRepository restaurantOwnerRepository;

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
      throw new BusinessException(ErrorCode.SESSION_NOT_ACCEPTING);
    }
    if (waitingRepository.existsByUserIdAndSessionIdAndStatusIn(
        loginUserId, session.getId(), ACTIVE_STATUSES)) {
      throw new BusinessException(ErrorCode.DUPLICATE_WAITING);
    }

    int nextNumber = waitingRepository.findMaxWaitingNumber(session.getId()) + 1;

    session.increaseCurrentCount();
    Waiting waiting = Waiting.register(
        loginUserId, restaurantId, session.getId(),
        nextNumber, request.partySize(), LocalDateTime.now());
    waitingRepository.save(waiting);

    log.info("Waiting registered. waitingId={}, sessionId={}, number={}, userId={}",
        waiting.getId(), session.getId(), nextNumber, loginUserId);

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
    waiting.call(LocalDateTime.now());
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
    log.info("Auto-cancelled {} waitings due to timeout (>{}min)", expired.size(), callTimeoutMinutes);
    return expired.size();
  }

  /* 공통 취소 로직: 상태 전이 + 세션 카운트 감소 */
  private void applyCancel(Waiting waiting) {
    WaitingSession session = waitingSessionService.findSessionOrThrow(waiting.getSessionId());
    waiting.cancel(LocalDateTime.now());
    session.decreaseCurrentCount();
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
}