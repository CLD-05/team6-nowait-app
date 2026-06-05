package com.nowait.global.exception;

<<<<<<< HEAD
public class GlobalExceptionHandler {

}
=======
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  // 비즈니스 예외 (서비스에서 throw new BusinessException(...))
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest request) {
    log.warn("BusinessException: {}", e.getMessage());
    ErrorCode code = e.getErrorCode();
    return ResponseEntity.status(code.getStatus())
        .body(ErrorResponse.of(code, request.getRequestURI()));
  }

  // 요청 바디 검증 실패 (@Valid 실패)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
    List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
        .map(fe -> new ErrorResponse.FieldError(
            fe.getField(),
            fe.getRejectedValue() == null ? "" : fe.getRejectedValue().toString(),
            fe.getDefaultMessage()))
        .toList();
    return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
        .body(ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, request.getRequestURI(), errors));
  }

  // JSON 파싱 실패 (잘못된 형식, enum 매칭 실패 등)
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e,
      HttpServletRequest request) {
    return ResponseEntity.status(ErrorCode.MALFORMED_JSON.getStatus())
        .body(ErrorResponse.of(ErrorCode.MALFORMED_JSON, request.getRequestURI()));
  }

  // 인증 실패 (시큐리티)
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException e, HttpServletRequest request) {
    return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.getStatus())
        .body(ErrorResponse.of(ErrorCode.INVALID_CREDENTIALS, request.getRequestURI()));
  }

  // 권한 부족 (시큐리티)
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
    return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getStatus())
        .body(ErrorResponse.of(ErrorCode.ACCESS_DENIED, request.getRequestURI()));
  }

  // 그 외 모든 예외 (서버 오류)
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAll(Exception e, HttpServletRequest request) {
    log.error("Unhandled exception", e);
    return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
        .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI()));
  }
}
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
