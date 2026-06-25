package com.nowait.domain.waiting.entity;

import com.nowait.domain.waiting.type.WaitingSessionStatus;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "waiting_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
       restaurant 도메인은 다른 분 담당.
       연관관계(@ManyToOne) 대신 FK ID(Long) 만 보관.
       머지 후 필요하면 연관관계로 전환 협의.
    */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WaitingSessionStatus status;

    @Column(name = "max_waiting_count", nullable = false)
    private int maxWaitingCount;

    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    private WaitingSession(Long restaurantId, LocalDate sessionDate,
                           int maxWaitingCount, LocalDateTime openedAt) {
        this.restaurantId = restaurantId;
        this.sessionDate = sessionDate;
        this.status = WaitingSessionStatus.OPEN;
        this.maxWaitingCount = maxWaitingCount;
        this.currentCount = 0;
        this.openedAt = openedAt;
    }

    /* 새 세션 오픈 (정적 팩토리) */
    public static WaitingSession open(Long restaurantId, LocalDate sessionDate,
                                      int maxWaitingCount, LocalDateTime openedAt) {
        if (maxWaitingCount < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "최대 대기 인원은 0 이상이어야 합니다.");
        }
        return new WaitingSession(restaurantId, sessionDate, maxWaitingCount, openedAt);
    }

    /* OPEN → PAUSED */
    public void pause() {
        if (status != WaitingSessionStatus.OPEN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "OPEN 상태에서만 일시정지 가능합니다.");
        }
        this.status = WaitingSessionStatus.PAUSED;
    }

    /* PAUSED → OPEN */
    public void resume() {
        if (status != WaitingSessionStatus.PAUSED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "PAUSED 상태에서만 재개 가능합니다.");
        }
        this.status = WaitingSessionStatus.OPEN;
    }

    /* OPEN/PAUSED → CLOSED */
    public void close(LocalDateTime closedAt) {
        if (status == WaitingSessionStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "이미 마감된 세션입니다.");
        }
        this.status = WaitingSessionStatus.CLOSED;
        this.closedAt = closedAt;
    }

    /* CLOSED → OPEN (당일 재오픈) */
    public void reopen(int maxWaitingCount, LocalDateTime reopenedAt) {
        if (status != WaitingSessionStatus.CLOSED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "마감된 세션만 재오픈할 수 있습니다.");
        }
        if (maxWaitingCount < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "최대 대기 인원은 0 이상이어야 합니다.");
        }
        this.status = WaitingSessionStatus.OPEN;
        this.maxWaitingCount = maxWaitingCount;
        this.currentCount = 0;
        this.closedAt = null;
        this.openedAt = reopenedAt;
    }

    /* 손님 등록 시 호출 */
    public void increaseCurrentCount() {
        if (!status.canAcceptWaiting()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "현재 웨이팅을 받을 수 없는 상태입니다.");
        }
        if (currentCount >= maxWaitingCount) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "최대 대기 인원을 초과했습니다.");
        }
        this.currentCount++;
    }

    /* 손님 취소/입장 시 호출 */
    public void decreaseCurrentCount() {
        if (currentCount <= 0) {
            return; // 음수 방어
        }
        this.currentCount--;
    }
}