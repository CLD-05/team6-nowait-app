package com.nowait.domain.waiting.redis;

import com.nowait.domain.waiting.type.WaitingStatus;

import java.util.Map;

/*
 * Redis Hash 에 저장된 웨이팅 1건의 상세 데이터.
 *
 * 필드:
 *   userId, sessionId, restaurantId : 식별자 (문자열로 저장됨)
 *   waitingNumber                   : 대기 번호
 *   partySize                       : 인원
 *   status                          : WAITING / CALLED / ENTERED / CANCELLED
 *   registeredAt                    : 등록 시각 (epoch millis)
 *   calledAt / enteredAt / canceledAt : 상태 전이 시각 (nullable)
 */
public record WaitingTokenData(
    Long userId,
    Long sessionId,
    Long restaurantId,
    int waitingNumber,
    int partySize,
    WaitingStatus status,
    long registeredAt,
    Long calledAt,
    Long enteredAt,
    Long canceledAt
) {

  /* Redis HGETALL 결과를 도메인 객체로 변환 */
  public static WaitingTokenData fromHash(Map<Object, Object> hash) {
    if (hash == null || hash.isEmpty()) return null;
    return new WaitingTokenData(
        parseLong(hash.get("userId")),
        parseLong(hash.get("sessionId")),
        parseLong(hash.get("restaurantId")),
        parseInt(hash.get("waitingNumber")),
        parseInt(hash.get("partySize")),
        WaitingStatus.valueOf(String.valueOf(hash.get("status"))),
        parseLong(hash.get("registeredAt")),
        parseLongOrNull(hash.get("calledAt")),
        parseLongOrNull(hash.get("enteredAt")),
        parseLongOrNull(hash.get("canceledAt"))
    );
  }

  private static Long parseLong(Object o) {
    return o == null ? null : Long.parseLong(String.valueOf(o));
  }

  private static Long parseLongOrNull(Object o) {
    if (o == null) return null;
    String s = String.valueOf(o);
    return s.isBlank() ? null : Long.parseLong(s);
  }

  private static int parseInt(Object o) {
    return Integer.parseInt(String.valueOf(o));
  }
}
