package com.nowait.domain.reservation.dto;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.redis.ReservationTokenData;
import com.nowait.domain.reservation.type.RejectionReason;
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
    String userName,
    Long slotId,
    LocalDate slotDate,
    LocalTime slotTime,
    int headcount,
    ReservationStatus status,
    LocalDateTime createdAt,
    LocalDateTime visitedAt,
    LocalDateTime canceledAt,
    LocalDateTime noShowAt,
    LocalDateTime rejectedAt,
    RejectionReason rejectionReason
) {
    /* DB 엔티티 기반 — 과거/조회 응답 */
    public static ReservationResponse from(Reservation reservation) {
        var restaurant = reservation.getRestaurant();
        var slot = reservation.getSlot();
        return new ReservationResponse(
            reservation.getReservationToken(),
            reservation.getId(),
            restaurant != null ? restaurant.getId() : null,
            restaurant != null ? restaurant.getName() : "매장",
            reservation.getUser().getName(),
            slot != null ? slot.getId() : null,
            slot != null ? slot.getSlotDate() : null,
            slot != null ? slot.getSlotTime() : null,
            reservation.getHeadcount(),
            reservation.getStatus(),
            reservation.getCreatedAt(),
            reservation.getVisitedAt(),
            reservation.getCanceledAt(),
            reservation.getNoShowAt(),
            reservation.getRejectedAt(),
            reservation.getRejectionReason()
        );
    }

    /* Redis Hash 기반 — Worker sync 전이라도 즉시 응답 가능 */
    public static ReservationResponse fromRedis(
        String token,
        ReservationTokenData data,
        String restaurantName,
        String userName,
        LocalDate slotDate,
        LocalTime slotTime
    ) {
        RejectionReason rejReason = data.rejectionReason() == null ? null
            : RejectionReason.valueOf(data.rejectionReason());
        return new ReservationResponse(
            token,
            null,
            data.restaurantId(),
            restaurantName,
            userName,
            data.slotId(),
            slotDate,
            slotTime,
            data.headcount(),
            data.status(),
            toLdt(data.createdAt()),
            toLdtNullable(data.visitedAt()),
            toLdtNullable(data.canceledAt()),
            toLdtNullable(data.noShowAt()),
            toLdtNullable(data.rejectedAt()),
            rejReason
        );
    }

    private static LocalDateTime toLdt(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), com.nowait.global.common.TimeZones.KST);
    }

    private static LocalDateTime toLdtNullable(Long millis) {
        return millis == null ? null : toLdt(millis);
    }
}
