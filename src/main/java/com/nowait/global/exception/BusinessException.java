package com.nowait.global.exception;

<<<<<<< HEAD
public class BusinessException extends RuntimeException {
	private final ErrorCode errorCode;
	
	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

}
=======
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
>>>>>>> af9714f01c8ab88ccbba7992a4dc2d9ec0b9693d
