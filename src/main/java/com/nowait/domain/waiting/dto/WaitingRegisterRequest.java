package com.nowait.domain.waiting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WaitingRegisterRequest(

    @NotNull(message = "인원 수는 필수입니다.") @Min(value = 1, message = "인원 수는 1 이상이어야 합니다.") @Max(value = 20, message = "인원 수는 20 이하여야 합니다.") Integer partySize) {
}