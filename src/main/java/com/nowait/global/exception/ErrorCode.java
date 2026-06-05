package com.nowait.global.exception;

import lombok.Getter;
<<<<<<< HEAD

@Getter
public enum ErrorCode {
	
	// =========================================================================
    // 🔥 [1] 팀 명세서 공통 에러 규격 (400, 401, 403, 404, 409, 500)
    // =========================================================================
    
    // 400 Bad Request : 필수 파라미터 누락 / 잘못된 요청
    INVALID_INPUT_VALUE(400, "C001", "올바르지 않은 입력값입니다. 필수 항목을 확인해 주세요."),
    
    // 401 Unauthorized : 토큰 없음 / 만료
    UNAUTHORIZED_USER(401, "C002", "인증 자격 증명이 유효하지 않거나 토큰이 만료되었습니다."),
    
    // 403 Forbidden : 권한 없음
    HANDLE_ACCESS_DENIED(403, "C003", "해당 요청에 대한 접근 권한이 없습니다."),
    
    // 404 Not Found : 리소스 없음
    RESOURCE_NOT_FOUND(404, "C004", "요청하신 리소스를 찾을 수 없습니다."),
    
    // 409 Conflict : 중복 / 상태 충돌
    DATA_DUPLICATION_CONFLICT(409, "C005", "이미 존재하는 데이터이거나 상태가 충돌합니다."),
    
    // 500 Internal Server Error : 서버 에러
    INTERNAL_SERVER_ERROR(500, "C006", "서버 내부 오류가 발생했습니다. 개발자에게 문의하세요."),


    // =========================================================================
    // 🍲 [2] 식당(Restaurant) 도메인 전용 커스텀 에러 (명세서 기반 세분화)
    // =========================================================================
    
    // 식당 상세 조회 시 없는 ID를 던졌을 때 (404 Not Found)
    RESTAURANT_NOT_FOUND(404, "R001", "존재하지 않는 식당입니다.");


    // --- 변수 및 생성자 영역 (enum을 지탱하는 기둥들입니다) ---
    private final int status;   // HTTP 상태 코드 (예: 400, 403, 404)
    private final String code;  // 우리 팀만의 고유 에러 코드 (프론트 확인용)
    private final String message;// 화면에 띄워줄 친절한 에러 메시지

    ErrorCode(int status, String code, String message) {
=======
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C400", "잘못된 입력값입니다."),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "C401", "요청 형식이 올바르지 않습니다."),

    // 401 Unauthorized
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A401", "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A402", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A403", "만료된 토큰입니다."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A404", "접근 권한이 없습니다."),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U404", "사용자를 찾을 수 없습니다."),
    RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "R404", "식당을 찾을 수 없습니다."),
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "SL404", "슬롯을 찾을 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RS404", "예약을 찾을 수 없습니다."),
    WAITING_NOT_FOUND(HttpStatus.NOT_FOUND, "W404", "웨이팅을 찾을 수 없습니다."),
    WAITING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "WS404", "운영 중인 웨이팅 세션이 없습니다."),

    // 409 Conflict
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U409", "이미 사용 중인 이메일입니다."),
    SLOT_FULL(HttpStatus.CONFLICT, "SL409", "해당 슬롯의 예약이 마감되었습니다."),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT, "RS409", "이미 해당 슬롯에 예약이 존재합니다."),
    ALREADY_CANCELLED_RESERVATION(HttpStatus.CONFLICT, "RS410", "이미 취소된 예약입니다."),
    CANNOT_CANCEL_RESERVATION(HttpStatus.CONFLICT, "RS411", "취소할 수 없는 예약 상태입니다."),
    DUPLICATE_SLOT(HttpStatus.CONFLICT, "SL410", "이미 존재하는 슬롯입니다."),
    DUPLICATE_WAITING(HttpStatus.CONFLICT, "W409", "이미 해당 식당의 웨이팅이 등록되어 있습니다."),
    WAITING_COUNT_EXCEEDED(HttpStatus.CONFLICT, "W410", "웨이팅 최대 팀 수를 초과했습니다."),
    ALREADY_PROCESSED_WAITING(HttpStatus.CONFLICT, "W411", "이미 처리된 웨이팅입니다."),

    // 400 Bad Request (도메인)
    SLOT_REMAIN_INVALID(HttpStatus.BAD_REQUEST, "SL400", "잔여 수가 전체 수를 초과할 수 없습니다."),
    WAITING_SESSION_PAUSED(HttpStatus.BAD_REQUEST, "WS400", "웨이팅이 일시정지 상태입니다."),
    RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "RS403", "본인의 예약이 아닙니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
        this.status = status;
        this.code = code;
        this.message = message;
    }
<<<<<<< HEAD
	

}
=======
}
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
