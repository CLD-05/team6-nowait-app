package com.nowait.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nowait.global.common.TimeZones;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String code,
    String message,
    String path,
    List<FieldError> errors) {

  public static ErrorResponse of(ErrorCode errorCode, String path) {
    return new ErrorResponse(
        LocalDateTime.now(TimeZones.KST),
        errorCode.getStatus().value(),
        errorCode.getCode(),
        errorCode.getMessage(),
        path,
        null);
  }

  public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldError> errors) {
    return new ErrorResponse(
        LocalDateTime.now(TimeZones.KST),
        errorCode.getStatus().value(),
        errorCode.getCode(),
        errorCode.getMessage(),
        path,
        errors);
  }

  public record FieldError(String field, String value, String reason) {
  }
}
