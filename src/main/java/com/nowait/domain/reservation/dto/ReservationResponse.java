package com.nowait.domain.reservation.dto;

import com.nowait.domain.reservation.entity.Reservation;
import com.nowait.domain.reservation.type.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservationResponse(

    Long reservationId,
    Long restaurantId,
    String restaurantName,
    Long slotId,
    LocalDate slotDate,
    LocalTime slotTime,
    int headcount,
    ReservationStatus status,
    LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getRestaurant().getId(),
            reservation.getRestaurant().getName(),
            reservation.getSlot().getId(),
            reservation.getSlot().getSlotDate(),
            reservation.getSlot().getSlotTime(),
            reservation.getHeadcount(),
            reservation.getStatus(),
            reservation.getCreatedAt()
        );
    }
}
