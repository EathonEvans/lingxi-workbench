package com.lingxi.workbench.framework.convention;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

/**
 * 全局统一接口响应体。
 *
 * <p>所有 Controller 接口均通过本记录返回统一格式的 JSON 响应，
 * 禁止直接返回业务对象或自定义响应结构。</p>
 *
 * @param code 业务结果码，成功为 200，失败时与 HTTP 状态码保持一致
 * @param data 响应数据，成功时携带业务数据，失败时为 null
 * @param msg  结果说明，成功时为"成功"，失败时为具体错误描述
 * @param <T>  响应数据类型
 */
public record ApiResponse<T>(int code, T data, String msg) {

    /** 成功状态码，取自 {@link HttpStatusEnum#OK} */
    private static final int SUCCESS_CODE = HttpStatusEnum.OK.getCode();
    /** 成功默认消息 */
    private static final String SUCCESS_MESSAGE = "成功";

    /**
     * 构建带数据的成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, data, SUCCESS_MESSAGE);
    }

    /**
     * 构建无数据的成功响应，用于创建、删除等无需返回数据的操作。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(SUCCESS_CODE, null, SUCCESS_MESSAGE);
    }

    /**
     * 构建失败响应。
     *
     * @param code    业务错误码，必须大于等于 400
     * @param message 错误描述，不能为空
     * @return 失败响应
     * @throws IllegalArgumentException 如果 code < 400 或 message 为空
     */
    public static ApiResponse<Void> failure(int code, String message) {
        // 校验：失败响应状态码必须在 4xx/5xx 范围
        if (code < HttpStatusEnum.BAD_REQUEST.getCode()) {
            throw new IllegalArgumentException("失败响应码必须大于等于400");
        }
        // 校验：失败响应必须提供错误描述
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("失败响应信息不能为空");
        }
        return new ApiResponse<>(code, null, message);
    }

    /**
     * 通过 {@link HttpStatusEnum} 构建失败响应。
     *
     * @param status  HTTP 状态枚举，不能为空且不能为 {@code OK}
     * @param message 错误描述
     * @return 失败响应
     * @throws IllegalArgumentException 如果 status 为 null
     */
    public static ApiResponse<Void> failure(HttpStatusEnum status, String message) {
        if (status == null) {
            throw new IllegalArgumentException("HTTP 状态不能为空");
        }
        return failure(status.getCode(), message);
    }
}
