package com.nowait.domain.reservation.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RejectionReason {
    SLOT_FULL("예약이 마감됐습니다."),
    STORE_CLOSED("매장 사정으로 해당 일시에 운영이 불가합니다."),
    PARTY_SIZE_UNAVAILABLE("요청하신 인원을 수용하기 어렵습니다."),
    SPECIAL_EVENT("단체 행사 예약으로 좌석이 부족합니다."),
    OTHER("기타 사유로 예약을 거부했습니다.");

    private final String description;
}
