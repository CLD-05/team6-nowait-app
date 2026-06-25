package com.nowait.global.s3;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImageCompleteRequest(
    @NotNull Long restaurantId,
    @NotBlank String imageKey
) {}
