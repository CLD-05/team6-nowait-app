<<<<<<< HEAD
package com.nowait.domain.slot.dto;

public class SlotUpdateRequest {

}
=======
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
}
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
