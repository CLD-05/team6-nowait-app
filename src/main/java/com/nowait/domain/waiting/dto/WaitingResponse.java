package com.nowait.domain.waiting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nowait.domain.waiting.entity.Waiting;
import com.nowait.domain.waiting.redis.WaitingTokenData;
import com.nowait.domain.waiting.type.WaitingStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/*
 * 웨이팅 응답 DTO.
 * Redis Hash (WaitingTokenData) 를 그대로 노출.
 *
 * waitingToken : 사용자/점주가 후속 API (취소/호출/입장) 호출 시 사용하는 식별자
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WaitingResponse(
    String waitingToken,
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

  public static WaitingResponse of(String token, WaitingTokenData data, Long aheadCount) {
    return new WaitingResponse(
        token,
        data.sessionId(),
        data.restaurantId(),
        data.userId(),
        data.waitingNumber(),
        data.partySize(),
        data.status(),
        aheadCount,
        toDateTime(data.registeredAt()),
        toDateTimeNullable(data.calledAt()),
        toDateTimeNullable(data.enteredAt()),
        toDateTimeNullable(data.canceledAt())
    );
  }

  public static WaitingResponse of(String token, WaitingTokenData data) {
    return of(token, data, null);
  }

  /* DB 엔티티 → 응답 (마이페이지 이력 조회용) */
  public static WaitingResponse from(Waiting w) {
    return new WaitingResponse(
        w.getWaitingToken(),
        w.getSessionId(),
        w.getRestaurantId(),
        w.getUserId(),
        w.getWaitingNumber(),
        w.getPartySize(),
        w.getStatus(),
        null,
        w.getRegisteredAt(),
        w.getCalledAt(),
        w.getEnteredAt(),
        w.getCanceledAt()
    );
  }

  private static LocalDateTime toDateTime(long millis) {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), com.nowait.global.common.TimeZones.KST);
  }

  private static LocalDateTime toDateTimeNullable(Long millis) {
    return millis == null ? null : toDateTime(millis);
  }
}
