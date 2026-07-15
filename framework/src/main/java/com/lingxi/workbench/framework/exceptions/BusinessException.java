package com.lingxi.workbench.framework.exceptions;

/**
 * 可预期的业务异常基类。
 */
public class BusinessException extends RuntimeException {

    private final int httpStatus;
    private final int code;

    public BusinessException(int httpStatus, int code, String message) {
        super(message);
        if (httpStatus < 400 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP 状态码必须在400到599之间");
        }
        if (code < 400) {
            throw new IllegalArgumentException("业务错误码必须大于等于400");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("异常信息不能为空");
        }
        this.httpStatus = httpStatus;
        this.code = code;
    }


    public BusinessException(String message) {
        super(message);
        this.httpStatus = 200;
        this.code = 500;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }
}
