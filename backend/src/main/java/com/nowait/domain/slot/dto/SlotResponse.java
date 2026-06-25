// domain/slot/dto/SlotResponse.java
package com.nowait.domain.slot.dto;

import com.nowait.domain.slot.entity.Slot;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SlotResponse {

    private Long restaurantId;
    private LocalDate date;
    private List<SlotInfo> slots;

    // 슬롯 목록 반환용
    public static SlotResponse of(Long restaurantId,
                                   LocalDate date,
                                   List<Slot> slots) {
        return SlotResponse.builder()
            .restaurantId(restaurantId)
            .date(date)
            .slots(slots.stream()
                .map(SlotInfo::from)
                .collect(Collectors.toList()))
            .build();
    }

    // 슬롯 단건 반환용 내부 클래스
    @Getter
    @Builder
    public static class SlotInfo {

        private Long slotId;
        private String slotTime;
        private int totalCount;
        private int remainCount;
        private boolean available;
        private int minHeadcount;
        private int maxHeadcount;

        public static SlotInfo from(Slot slot) {
            return SlotInfo.builder()
                .slotId(slot.getId())
                .slotTime(slot.getSlotTime().toString())
                .totalCount(slot.getTotalCount())
                .remainCount(slot.getRemainCount())
                .available(slot.isAvailable())
                .minHeadcount(slot.getMinHeadcount())
                .maxHeadcount(slot.getMaxHeadcount())
                .build();
        }
    }
}
