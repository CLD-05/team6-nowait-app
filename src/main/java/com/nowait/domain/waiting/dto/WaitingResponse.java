package com.nowait.domain.waiting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.type.WaitingStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitingResponse(
    Long waitingId,
    Long sessionId,
    Long restaurantId,
    Long userId,
    int waitingNumber,
    int partySize,
    WaitingStatus status,
    Long aheadCount,
    LocalDateTime registeredAt,
    LocalDateTime calledAt,
    LocalDateTime enteredAt,
    LocalDateTime canceledAt) {

  public static WaitingResponse of(Waiting w, Long aheadCount) {
    return new WaitingResponse(
        w.getId(),
        w.getSessionId(),
        w.getRestaurantId(),
        w.getUserId(),
        w.getWaitingNumber(),
        w.getPartySize(),
        w.getStatus(),
        aheadCount,
        w.getRegisteredAt(),
        w.getCalledAt(),
        w.getEnteredAt(),
        w.getCanceledAt());
  }

  public static WaitingResponse from(Waiting w) {
    return of(w, null);
  }
}