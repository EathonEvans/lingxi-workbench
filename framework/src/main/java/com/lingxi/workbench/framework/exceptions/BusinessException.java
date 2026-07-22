package com.lingxi.workbench.framework.exceptions;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

/**
 * 可预期的业务异常基类。
 *
 * <p>所有可预期、需要向客户端返回友好提示的业务异常均应继承本类。
 * 全局异常处理器根据本异常返回对应 HTTP 状态码和业务错误码。</p>
 *
 * <p>本类禁止直接使用 {@link HttpStatusEnum#OK} 及小于 400 的错误码。</p>
 */
public class BusinessException extends RuntimeException {

    /** 对应的 HTTP 状态，用于全局异常处理器设置响应状态码 */
    private final HttpStatusEnum httpStatus;
    /** 业务错误码，用于客户端识别具体错误类型 */
    private final int code;

    /**
     * 构造业务异常，允许自定义错误码。
     *
     * @param httpStatus HTTP 状态枚举，不能为空且不能为 {@code OK}
     * @param code       业务错误码，必须大于等于 400
     * @param message    错误描述，不能为空
     * @throws IllegalArgumentException 参数校验不通过时抛出
     */
    public BusinessException(HttpStatusEnum httpStatus, int code, String message) {
        super(message);
        // 业务异常必须对应失败 HTTP 状态
        if (httpStatus == null || httpStatus == HttpStatusEnum.OK) {
            throw new IllegalArgumentException("业务异常必须使用失败状态");
        }
        // 业务错误码必须在 4xx/5xx 范围
        if (code < HttpStatusEnum.BAD_REQUEST.getCode()) {
            throw new IllegalArgumentException("业务错误码必须大于等于400");
        }
        // 必须提供可读的错误描述
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("异常信息不能为空");
        }
        this.httpStatus = httpStatus;
        this.code = code;
    }

    /**
     * 构造业务异常，错误码默认使用 HTTP 状态码。
     *
     * @param httpStatus HTTP 状态枚举
     * @param message    错误描述
     */
    public BusinessException(HttpStatusEnum httpStatus, String message) {
        this(httpStatus, httpStatus.getCode(), message);
    }

    /**
     * 获取对应的 HTTP 状态枚举。
     *
     * @return HTTP 状态
     */
    public HttpStatusEnum getHttpStatus() {
        return httpStatus;
    }

    /**
     * 获取业务错误码。
     *
     * @return 业务错误码
     */
    public int getCode() {
        return code;
    }
}
