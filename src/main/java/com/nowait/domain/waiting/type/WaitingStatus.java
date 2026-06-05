package com.nowait.domain.waiting.type;

/*
  개별 웨이팅(손님) 상태
  - WAITING:    대기 중
  - CALLED:     점주가 호출 (입장 안내)
  - ENTERED:    입장 완료
  - CANCELLED:  취소 (손님 직접 취소 또는 점주가 취소 처리)

  상태 전이:
      WAITING → CALLED → ENTERED  (정상 흐름)
      WAITING → CANCELLED          (손님 또는 점주 취소)
      CALLED  → CANCELLED          (호출 후 노쇼 등으로 취소), 호출 후 10분 동안 응답 없으면 자동 취소
      ENTERED → (종착 상태, 변경 불가)
      CANCELLED → (종착 상태, 변경 불가)
*/

public enum WaitingStatus {

  WAITING,
  CALLED,
  ENTERED,
  CANCELLED;

  public boolean isTerminal() {
    return this == ENTERED || this == CANCELLED;
  }

  public boolean isActive() {
    return this == WAITING || this == CALLED;
  }

  /* 손님이 직접 취소 가능한 상태인가? */
  public boolean canCancelByUser() {
    return this == WAITING || this == CALLED;
  }
}