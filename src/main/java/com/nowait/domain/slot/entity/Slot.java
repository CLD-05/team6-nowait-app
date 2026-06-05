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

    @Builder
    public Slot(Restaurant restaurant, LocalDate slotDate,
                LocalTime slotTime, int totalCount) {
        this.restaurant = restaurant;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.totalCount = totalCount;
        this.remainCount = totalCount;
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
}
