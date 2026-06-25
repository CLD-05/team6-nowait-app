package com.nowait.domain.waiting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WaitingSessionOpenRequest(

    @NotNull(message = "최대 대기 인원은 필수입니다.") @Min(value = 1, message = "최대 대기 인원은 1 이상이어야 합니다.") @Max(value = 999, message = "최대 대기 인원은 999 이하여야 합니다.") Integer maxWaitingCount) {
}