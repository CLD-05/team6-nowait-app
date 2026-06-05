package com.nowait.domain.waiting.type;

/*
   웨이팅 세션 상태
   - OPEN:    웨이팅 등록 가능
   - PAUSED:  일시 정지 (점주가 잠시 멈춤, 등록 불가)
   - CLOSED:  마감 (세션 종료, 재오픈 불가)

   상태 전이:
       OPEN ⇄ PAUSED
       OPEN → CLOSED
       PAUSED → CLOSED
       CLOSED → (재오픈 불가, 같은 날짜는 새 세션 생성 불가 — DDL UNIQUE 제약)
*/
public enum WaitingSessionStatus {

  OPEN,
  PAUSED,
  CLOSED;

  public boolean isClosed() {
    return this == CLOSED;
  }

  public boolean isOpen() {
    return this == OPEN;
  }

  public boolean isPaused() {
    return this == PAUSED;
  }

  /* 손님 웨이팅 등록 가능한 상태인가? */
  public boolean canAcceptWaiting() {
    return this == OPEN;
  }
}