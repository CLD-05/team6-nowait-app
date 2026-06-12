package com.nowait.domain.reservation.dto;

import com.nowait.domain.reservation.type.RejectionReason;
import jakarta.validation.constraints.NotNull;

public record RejectReservationRequest(
    @NotNull(message = "거부 사유는 필수입니다.")
    RejectionReason reason
) {}
