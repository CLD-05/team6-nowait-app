package com.nowait.global.exception;

<<<<<<< HEAD
public class ErrorResponse {

}
=======
import com.fasterxml.jackson.annotation.JsonInclude;

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
        LocalDateTime.now(),
        errorCode.getStatus().value(),
        errorCode.getCode(),
        errorCode.getMessage(),
        path,
        null);
  }

  public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldError> errors) {
    return new ErrorResponse(
        LocalDateTime.now(),
        errorCode.getStatus().value(),
        errorCode.getCode(),
        errorCode.getMessage(),
        path,
        errors);
  }

  public record FieldError(String field, String value, String reason) {
  }
}
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
