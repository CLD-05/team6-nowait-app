// domain/slot/dto/SlotCreateRequest.java
package com.nowait.domain.slot.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SlotCreateRequest {

    @NotNull(message = "날짜를 입력해주세요.")
    private LocalDate slotDate;

    @NotNull(message = "시간을 입력해주세요.")
    private LocalTime slotTime;

    @Min(value = 1, message = "슬롯 수는 1 이상이어야 합니다.")
    private int totalCount;
}