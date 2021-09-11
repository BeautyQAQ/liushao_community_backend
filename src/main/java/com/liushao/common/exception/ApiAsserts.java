package com.liushao.common.exception;

import com.liushao.common.api.ApiErrorCode;

public class ApiAsserts {
    /**
     * 抛失败异常
     *
     * @param message 说明
     */
    public static void fail(String message) {
        throw new ApiException(message);
    }

    /**
     * 抛失败异常
     *
     * @param errorCode 状态码
     */
    public static void fail(ApiErrorCode errorCode) {
        throw new ApiException(errorCode);
    }
}
