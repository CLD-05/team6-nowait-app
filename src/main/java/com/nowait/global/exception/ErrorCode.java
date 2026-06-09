package com.nowait.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C400", "잘못된 입력값입니다."),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "C401", "요청 형식이 올바르지 않습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "C402", "현재 상태에서는 해당 작업을 수행할 수 없습니다."),
    SLOT_REMAIN_INVALID(HttpStatus.BAD_REQUEST, "SL400", "잔여 수가 전체 수를 초과할 수 없습니다."),
    WAITING_SESSION_PAUSED(HttpStatus.BAD_REQUEST, "WS400", "웨이팅이 일시정지 상태입니다."),

    // 401 Unauthorized
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A401", "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A402", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A403", "만료된 토큰입니다."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A404", "접근 권한이 없습니다."),
    NOT_RESTAURANT_OWNER(HttpStatus.FORBIDDEN, "A405", "해당 식당의 점주가 아닙니다."),
    RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "RS403", "본인의 예약이 아닙니다."),
    REVIEW_ACCESS_DENIED(HttpStatus.FORBIDDEN, "RV403", "본인의 리뷰가 아닙니다."),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U404", "사용자를 찾을 수 없습니다."),
    RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "R404", "식당을 찾을 수 없습니다."),
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "SL404", "슬롯을 찾을 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RS404", "예약을 찾을 수 없습니다."),
    WAITING_NOT_FOUND(HttpStatus.NOT_FOUND, "W404", "웨이팅을 찾을 수 없습니다."),
    WAITING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "WS404", "운영 중인 웨이팅 세션이 없습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "RV404", "리뷰를 찾을 수 없습니다."),

    // 409 Conflict
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U409", "이미 사용 중인 이메일입니다."),
    SLOT_FULL(HttpStatus.CONFLICT, "SL409", "해당 슬롯의 예약이 마감되었습니다."),
    DUPLICATE_SLOT(HttpStatus.CONFLICT, "SL410", "이미 존재하는 슬롯입니다."),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT, "RS409", "이미 해당 슬롯에 예약이 존재합니다."),
    ALREADY_CANCELLED_RESERVATION(HttpStatus.CONFLICT, "RS410", "이미 취소된 예약입니다."),
    CANNOT_CANCEL_RESERVATION(HttpStatus.CONFLICT, "RS411", "취소할 수 없는 예약 상태입니다."),
    DUPLICATE_WAITING(HttpStatus.CONFLICT, "W409", "이미 해당 식당의 웨이팅이 등록되어 있습니다."),
    WAITING_COUNT_EXCEEDED(HttpStatus.CONFLICT, "W410", "웨이팅 최대 팀 수를 초과했습니다."),
    ALREADY_PROCESSED_WAITING(HttpStatus.CONFLICT, "W411", "이미 처리된 웨이팅입니다."),
    WAITING_SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "WS409", "오늘 이미 오픈된 세션이 있습니다."),
    WAITING_SESSION_NOT_ACCEPTING(HttpStatus.CONFLICT, "WS410", "현재 웨이팅을 받을 수 없는 상태입니다."),
    OWNER_ALREADY_EXISTS(HttpStatus.CONFLICT, "RO409", "이미 다른 식당의 점주로 등록된 유저입니다."),
    RESTAURANT_ALREADY_HAS_OWNER(HttpStatus.CONFLICT, "RO410", "이미 점주가 지정된 식당입니다."),
    REVIEW_NOT_VISITED(HttpStatus.CONFLICT, "RV409", "방문 완료된 예약에 대해서만 리뷰를 작성할 수 있습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "RV410", "이미 작성된 리뷰가 있습니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

}
