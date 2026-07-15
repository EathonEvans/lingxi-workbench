package com.lingxi.workbench.framework.exceptions;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

/**
 * 可预期的业务异常基类。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatusEnum httpStatus;
    private final int code;

    public BusinessException(HttpStatusEnum httpStatus, int code, String message) {
        super(message);
        if (httpStatus == null || httpStatus == HttpStatusEnum.OK) {
            throw new IllegalArgumentException("业务异常必须使用失败状态");
        }
        if (code < HttpStatusEnum.BAD_REQUEST.value()) {
            throw new IllegalArgumentException("业务错误码必须大于等于400");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("异常信息不能为空");
        }
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public BusinessException(HttpStatusEnum httpStatus, String message) {
        this(httpStatus, httpStatus.value(), message);
    }

    public HttpStatusEnum getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }
}
