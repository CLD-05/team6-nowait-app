package com.nowait.domain.reservation.redis;

import com.nowait.domain.reservation.type.ReservationStatus;

import java.util.Map;

/*
 * Redis Hash 에 저장된 예약 1건의 상세 데이터.
 *
 * 필드:
 *   userId, restaurantId, slotId : 식별자
 *   headcount                    : 인원
 *   status                       : CONFIRMED / VISITED / CANCELLED / NO_SHOW
 *   createdAt                    : 생성 시각 (epoch millis)
 *   reservationTime              : 예약 시각 (slot date+time, epoch millis) — 노쇼 스케줄러 기준
 *   visitedAt / canceledAt / noShowAt : 상태 전이 시각 (nullable)
 */
public record ReservationTokenData(
    Long userId,
    Long restaurantId,
    Long slotId,
    int headcount,
    ReservationStatus status,
    long createdAt,
    long reservationTime,
    Long visitedAt,
    Long canceledAt,
    Long noShowAt
) {

  public static ReservationTokenData fromHash(Map<Object, Object> hash) {
    if (hash == null || hash.isEmpty()) return null;
    return new ReservationTokenData(
        parseLong(hash.get("userId")),
        parseLong(hash.get("restaurantId")),
        parseLong(hash.get("slotId")),
        parseInt(hash.get("headcount")),
        ReservationStatus.valueOf(String.valueOf(hash.get("status"))),
        parseLong(hash.get("createdAt")),
        parseLong(hash.get("reservationTime")),
        parseLongOrNull(hash.get("visitedAt")),
        parseLongOrNull(hash.get("canceledAt")),
        parseLongOrNull(hash.get("noShowAt"))
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
