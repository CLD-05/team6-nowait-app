package com.nowait.domain.waiting.entity;

import com.nowait.domain.waiting.type.WaitingStatus;
import com.nowait.global.exception.BusinessException;
import com.nowait.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "waiting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waiting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /* Redis 의 waitingToken — Worker 가 idempotent upsert 를 위해 사용하는 자연키 */
  @Column(name = "waiting_token", nullable = false, length = 36, unique = true)
  private String waitingToken;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "restaurant_id", nullable = false)
  private Long restaurantId;

  @Column(name = "session_id", nullable = false)
  private Long sessionId;

  @Column(name = "waiting_number", nullable = false)
  private int waitingNumber;

  @Column(name = "party_size", nullable = false)
  private int partySize;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private WaitingStatus status;

  @Column(name = "registered_at", nullable = false)
  private LocalDateTime registeredAt;

  @Column(name = "called_at")
  private LocalDateTime calledAt;

  @Column(name = "entered_at")
  private LocalDateTime enteredAt;

  @Column(name = "canceled_at")
  private LocalDateTime canceledAt;

  @Column(name = "near_call_notified", nullable = false)
  private boolean nearCallNotified = false;

  private Waiting(String waitingToken, Long userId, Long restaurantId, Long sessionId,
      int waitingNumber, int partySize, LocalDateTime registeredAt) {
    this.waitingToken = waitingToken;
    this.userId = userId;
    this.restaurantId = restaurantId;
    this.sessionId = sessionId;
    this.waitingNumber = waitingNumber;
    this.partySize = partySize;
    this.status = WaitingStatus.WAITING;
    this.registeredAt = registeredAt;
  }

  public static Waiting register(String waitingToken, Long userId, Long restaurantId,
      Long sessionId, int waitingNumber, int partySize, LocalDateTime registeredAt) {
    if (partySize <= 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인원 수는 1 이상이어야 합니다.");
    }
    if (waitingNumber <= 0) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "대기 번호는 1 이상이어야 합니다.");
    }
    return new Waiting(waitingToken, userId, restaurantId, sessionId,
        waitingNumber, partySize, registeredAt);
  }

  /*
   * Worker 전용 — Redis Hash 의 현재 상태로 DB 행을 동기화한다.
   * 메시지가 어떤 순서로 도착해도 멱등성을 보장하기 위해 절대 상태로 덮어쓴다.
   */
  public void syncFromRedis(WaitingStatus status,
      LocalDateTime calledAt, LocalDateTime enteredAt, LocalDateTime canceledAt) {
    this.status = status;
    this.calledAt = calledAt;
    this.enteredAt = enteredAt;
    this.canceledAt = canceledAt;
  }

  /* WAITING → CALLED */
  public void call(LocalDateTime calledAt) {
    if (status != WaitingStatus.WAITING) {
      throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
          "대기 중인 손님만 호출 가능합니다.");
    }
    this.status = WaitingStatus.CALLED;
    this.calledAt = calledAt;
  }

  /* CALLED → ENTERED */
  public void enter(LocalDateTime enteredAt) {
    if (status != WaitingStatus.CALLED) {
      throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
          "호출된 손님만 입장 처리 가능합니다.");
    }
    this.status = WaitingStatus.ENTERED;
    this.enteredAt = enteredAt;
  }

  /* WAITING/CALLED → CANCELLED */
  public void cancel(LocalDateTime canceledAt) {
    if (status.isTerminal()) {
      throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
          "이미 종료된 웨이팅입니다.");
    }
    this.status = WaitingStatus.CANCELLED;
    this.canceledAt = canceledAt;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  /* 앞 5팀 알림 발송 마킹 (중복 발송 방지) */
  public void markNearCallNotified() {
    this.nearCallNotified = true;
  }
}