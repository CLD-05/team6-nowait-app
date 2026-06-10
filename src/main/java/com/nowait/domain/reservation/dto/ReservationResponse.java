package com.nowait.domain.reservation.dto;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.redis.ReservationTokenData;
import com.nowait.domain.reservation.type.ReservationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/*
 * 예약 응답.
 *
 * 식별자:
 *   - reservationToken: 1차 식별자 (모든 후속 액션의 path 변수)
 *   - reservationId   : Worker 가 DB sync 한 후에만 채워짐 (그 전엔 null)
 */
public record ReservationResponse(
    String reservationToken,
    Long reservationId,
    Long restaurantId,
    String restaurantName,
    Long slotId,
    LocalDate slotDate,
    LocalTime slotTime,
    int headcount,
    ReservationStatus status,
    LocalDateTime createdAt,
    LocalDateTime visitedAt,
    LocalDateTime canceledAt,
    LocalDateTime noShowAt
) {
    /* DB 엔티티 기반 — 과거/조회 응답 */
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
            reservation.getReservationToken(),
            reservation.getId(),
            reservation.getRestaurant().getId(),
            reservation.getRestaurant().getName(),
            reservation.getSlot().getId(),
            reservation.getSlot().getSlotDate(),
            reservation.getSlot().getSlotTime(),
            reservation.getHeadcount(),
            reservation.getStatus(),
            reservation.getCreatedAt(),
            reservation.getVisitedAt(),
            reservation.getCanceledAt(),
            reservation.getNoShowAt()
        );
    }

    /* Redis Hash 기반 — Worker sync 전이라도 즉시 응답 가능 */
    public static ReservationResponse fromRedis(
        String token,
        ReservationTokenData data,
        String restaurantName,
        LocalDate slotDate,
        LocalTime slotTime
    ) {
        return new ReservationResponse(
            token,
            null,
            data.restaurantId(),
            restaurantName,
            data.slotId(),
            slotDate,
            slotTime,
            data.headcount(),
            data.status(),
            toLdt(data.createdAt()),
            toLdtNullable(data.visitedAt()),
            toLdtNullable(data.canceledAt()),
            toLdtNullable(data.noShowAt())
        );
    }

    private static LocalDateTime toLdt(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private static LocalDateTime toLdtNullable(Long millis) {
        return millis == null ? null : toLdt(millis);
    }
}
