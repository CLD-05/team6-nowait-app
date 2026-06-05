package com.nowait.domain.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequest(

    @NotNull(message = "식당 ID는 필수입니다.")
    Long restaurantId,

    @NotNull(message = "슬롯 ID는 필수입니다.")
    Long slotId,

    @Min(value = 1, message = "예약 인원은 1명 이상이어야 합니다.")
    int headcount
) {}
