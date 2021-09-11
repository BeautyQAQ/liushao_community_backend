package com.liushao.common.exception;

import com.liushao.common.api.ApiErrorCode;

public class ApiException extends RuntimeException {
    private ApiErrorCode errorCode;

    public ApiException(ApiErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(String message) {
        super(message);
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
