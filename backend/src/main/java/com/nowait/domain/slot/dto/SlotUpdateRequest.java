// domain/slot/dto/SlotUpdateRequest.java
package com.nowait.domain.slot.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SlotUpdateRequest {

    @Min(value = 1, message = "슬롯 수는 1 이상이어야 합니다.")
    private int totalCount;
    
    @Min(value = 1, message = "최소 인원은 1 이상이어야 합니다.")
    private Integer minHeadcount;

    private Integer maxHeadcount;
}
