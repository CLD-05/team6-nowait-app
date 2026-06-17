package com.nowait.domain.waiting.service;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.restaurant.repository.RestaurantRepository;
import com.nowait.domain.waiting.dto.WaitingSessionOpenRequest;
import com.nowait.domain.waiting.dto.WaitingSessionResponse;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.redis.WaitingRedisLuaExecutor;
import com.nowait.domain.waiting.repository.WaitingSessionRepository;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitingSessionService {

  private final WaitingSessionRepository waitingSessionRepository;
  private final RestaurantRepository restaurantRepository;
  private final WaitingRedisLuaExecutor waitingRedis;
  

  public WaitingSessionResponse getTodaySession(Long restaurantId) {
    WaitingSession session = waitingSessionRepository
        .findByRestaurantIdAndSessionDate(restaurantId, LocalDate.now())
        .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_SESSION_NOT_FOUND));
    /* currentCount 는 Redis 가 source of truth — 응답 시점에 덮어쓰기 */
    int liveCount = waitingRedis.getCount(session.getId());
    return WaitingSessionResponse.from(session, liveCount);
  }

  @Transactional
  public WaitingSessionResponse openSession(Long restaurantId, Long loginUserId,
      WaitingSessionOpenRequest request) {
    verifyOwnership(restaurantId, loginUserId);

    LocalDate today = LocalDate.now();
    if (waitingSessionRepository.existsByRestaurantIdAndSessionDate(restaurantId, today)) {
      throw new BusinessException(ErrorCode.WAITING_SESSION_ALREADY_EXISTS);
    }

    WaitingSession session = WaitingSession.open(
        restaurantId, today, request.maxWaitingCount(), LocalDateTime.now());
    waitingSessionRepository.save(session);
    waitingRedis.initSession(session.getId());

    log.info("Waiting session opened. sessionId={}, restaurantId={}", session.getId(), restaurantId);
    return WaitingSessionResponse.from(session, 0);
  }

  @Transactional
  public WaitingSessionResponse pauseSession(Long sessionId, Long loginUserId) {
    WaitingSession session = findSessionOrThrow(sessionId);
    verifyOwnership(session.getRestaurantId(), loginUserId);
    session.pause();
    log.info("Waiting session paused. sessionId={}", sessionId);
    return WaitingSessionResponse.from(session, waitingRedis.getCount(sessionId));
  }

  @Transactional
  public WaitingSessionResponse resumeSession(Long sessionId, Long loginUserId) {
    WaitingSession session = findSessionOrThrow(sessionId);
    verifyOwnership(session.getRestaurantId(), loginUserId);
    session.resume();
    log.info("Waiting session resumed. sessionId={}", sessionId);
    return WaitingSessionResponse.from(session, waitingRedis.getCount(sessionId));
  }

  @Transactional
  public WaitingSessionResponse closeSession(Long sessionId, Long loginUserId) {
    WaitingSession session = findSessionOrThrow(sessionId);
    verifyOwnership(session.getRestaurantId(), loginUserId);
    session.close(LocalDateTime.now());
    waitingRedis.clearSession(sessionId);
    log.info("Waiting session closed. sessionId={}", sessionId);
    return WaitingSessionResponse.from(session, 0);
  }

  public WaitingSession findSessionOrThrow(Long sessionId) {
    return waitingSessionRepository.findById(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.WAITING_SESSION_NOT_FOUND));
  }

  private void verifyOwnership(Long restaurantId, Long loginUserId) {
    Restaurant restaurant = restaurantRepository.findById(restaurantId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
    if (!loginUserId.equals(restaurant.getOwnerId())) {
      throw new BusinessException(ErrorCode.NOT_RESTAURANT_OWNER);
    }
  }
}
