// domain/slot/entity/Slot.java
package com.nowait.domain.slot.entity;

import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.global.common.BaseTimeEntity;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "slots",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"restaurant_id", "slot_date", "slot_time"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "remain_count", nullable = false)
    private int remainCount;
    
    @Column(name = "min_headcount", nullable = true)
    private int minHeadcount = 1;
    
    @Column (name = "max_headcount", nullable = true)
    private Integer maxHeadcount;

    @Builder
    public Slot(Restaurant restaurant, LocalDate slotDate, LocalTime slotTime,
    		int totalCount, Integer minHeadcount, Integer maxHeadcount) {
        this.restaurant = restaurant;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.totalCount = totalCount;
        this.remainCount = totalCount;
     // 📜 값을 안 넣어주면(Null이면) 기본값 1이 들어가도록 안전장치 설정!
        this.minHeadcount = (minHeadcount != null) ? minHeadcount : 1;
        this.maxHeadcount = maxHeadcount; // 최대 인원은 없으면 없는 대로 Null 유지
    }

    public void decrease() {
        if (this.remainCount <= 0) {
            throw new BusinessException(ErrorCode.SLOT_FULL);
        }
        this.remainCount--;
    }

    public void increase() {
        if (this.remainCount >= this.totalCount) {
            throw new BusinessException(ErrorCode.SLOT_REMAIN_INVALID);
        }
        this.remainCount++;
    }

    public boolean isAvailable() {
        return this.remainCount > 0;
    }
    
    public void updateTotalCount(int newTotalCount, int diff) {
        this.totalCount = newTotalCount;
        this.remainCount = Math.max(0, this.remainCount + diff);
    }
    
    // 점주가 슬롯 인원 제한을 수정할 때 사용하는 비즈니스 메서드
    public void updateHeadcountRestrictions(Integer minHeadcount, Integer maxHeadcount) {
        if (minHeadcount != null) {
            this.minHeadcount = minHeadcount;
        }
        this.maxHeadcount = maxHeadcount; // 최대 인원은 Null로 변경될 수도 있으므로 그대로 갱신
    }
}
