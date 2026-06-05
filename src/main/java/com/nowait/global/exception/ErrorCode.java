package com.nowait.global.exception;

import lombok.Getter;

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
        this.status = status;
        this.code = code;
        this.message = message;
    }
	

}
