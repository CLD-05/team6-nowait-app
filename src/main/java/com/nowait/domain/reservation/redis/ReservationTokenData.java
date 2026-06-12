package com.nowait.domain.reservation.redis;

import com.nowait.domain.reservation.type.ReservationStatus;

import java.util.Map;

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
    Long noShowAt,
    Long rejectedAt,
    String rejectionReason
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
        parseLongOrNull(hash.get("noShowAt")),
        parseLongOrNull(hash.get("rejectedAt")),
        parseStringOrNull(hash.get("rejectionReason"))
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

  private static String parseStringOrNull(Object o) {
    if (o == null) return null;
    String s = String.valueOf(o);
    return s.isBlank() ? null : s;
  }
}
