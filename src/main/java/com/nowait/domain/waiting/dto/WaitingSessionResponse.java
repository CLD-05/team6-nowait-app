package com.nowait.domain.waiting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.type.WaitingSessionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * 웨이팅 세션 응답.
 * currentCount 는 Redis 의 실시간 카운터를 받아오기 때문에
 * from(session, liveCount) 형태로 외부에서 주입.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitingSessionResponse(
    Long sessionId,
    Long restaurantId,
    LocalDate sessionDate,
    WaitingSessionStatus status,
    int maxWaitingCount,
    int currentCount,
    LocalDateTime openedAt,
    LocalDateTime closedAt) {

  public static WaitingSessionResponse from(WaitingSession session, int liveCount) {
    return new WaitingSessionResponse(
        session.getId(),
        session.getRestaurantId(),
        session.getSessionDate(),
        session.getStatus(),
        session.getMaxWaitingCount(),
        liveCount,
        session.getOpenedAt(),
        session.getClosedAt());
  }
}
