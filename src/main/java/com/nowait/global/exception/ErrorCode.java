package com.nowait.global.exception;

import lombok.Getter;
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

  // 409 Conflict
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U409", "이미 사용 중인 이메일입니다."),

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