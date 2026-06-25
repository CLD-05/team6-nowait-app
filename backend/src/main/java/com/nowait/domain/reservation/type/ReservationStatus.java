package com.nowait.domain.reservation.type;

public enum ReservationStatus {
    PENDING,      // 예약 대기 (점주 승인 전)
    CONFIRMED,    // 예약 확정
    VISITED,      // 방문 완료
    CANCELLED,    // 사용자 취소
    NO_SHOW,      // 노쇼
    REJECTED      // 점주 거부
}
