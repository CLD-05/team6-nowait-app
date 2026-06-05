package com.nowait.domain.waiting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nowait.domain.waiting.entity.WaitingSession;
import com.nowait.domain.waiting.type.WaitingSessionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

  public static WaitingSessionResponse from(WaitingSession session) {
    return new WaitingSessionResponse(
        session.getId(),
        session.getRestaurantId(),
        session.getSessionDate(),
        session.getStatus(),
        session.getMaxWaitingCount(),
        session.getCurrentCount(),
        session.getOpenedAt(),
        session.getClosedAt());
  }
}