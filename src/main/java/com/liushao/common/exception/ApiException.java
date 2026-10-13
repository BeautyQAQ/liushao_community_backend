package com.liushao.common.exception;

import com.liushao.common.api.ApiErrorCode;

public class ApiException extends RuntimeException {
  private final int status;

  public ApiException(int status, String message) {
    super(message);
    this.status = status;
  }

  public ApiException(String message) {
    this(400, message);
  }

  public ApiException(ApiErrorCode code) {
    this(code.getCode() >= 400 ? code.getCode() : 400, code.getMessage());
  }

  public int getStatus() {
    return status;
  }
}
