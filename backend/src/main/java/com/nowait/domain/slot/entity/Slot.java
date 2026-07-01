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
    
    @Column(name = "min_headcount", nullable = false)
    private int minHeadcount = 1;

    @Column(name = "max_headcount", nullable = false)
    private int maxHeadcount = 8;

    @Builder
    public Slot(Restaurant restaurant, LocalDate slotDate, LocalTime slotTime,
    		int totalCount, Integer minHeadcount, Integer maxHeadcount) {
        this.restaurant = restaurant;
        this.slotDate = slotDate;
        this.slotTime = slotTime;
        this.totalCount = totalCount;
        this.remainCount = totalCount;
        // 📜 값 미지정 시 ERD 기본값(min=1, max=8) 적용
        this.minHeadcount = (minHeadcount != null) ? minHeadcount : 1;
        this.maxHeadcount = (maxHeadcount != null) ? maxHeadcount : 8;
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

    /*
     * Worker sync 재조정 전용 — 경계값에서 예외를 던지지 않고 멱등하게 조정한다.
     *
     * ⚠️ decrease()/increase() 는 API 예약/취소 경로에서 정원 위반을 막기 위해 예외를
     *    던지지만, Worker 는 Redis(정합성 소스)를 DB 에 반영하는 "재조정" 경로다.
     *    재조정 중 예외를 던지면 그 호출이 @Transactional(REQUIRED) 로 상위 트랜잭션에
     *    합류한 상태라 공유 트랜잭션이 rollback-only 로 마킹되고, 예외를 catch 해도
     *    상위 커밋이 UnexpectedRollbackException 으로 실패해 정상 토큰까지 DLQ 로 간다.
     *    따라서 이미 0/총원 경계에 도달했으면 조용히 유지(clamp)한다.
     */
    public void decreaseForSync() {
        if (this.remainCount > 0) {
            this.remainCount--;
        }
    }

    public void increaseForSync() {
        if (this.remainCount < this.totalCount) {
            this.remainCount++;
        }
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
        if (maxHeadcount != null) {
            this.maxHeadcount = maxHeadcount;
        }
    }
}
