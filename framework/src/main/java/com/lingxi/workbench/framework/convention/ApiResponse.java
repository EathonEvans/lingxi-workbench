package com.lingxi.workbench.framework.convention;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

/**
 * 全局统一接口响应体。
 *
 * @param code 业务结果码，第一版与 HTTP 状态码保持一致
 * @param data 响应数据，失败时不返回该字段
 * @param msg  结果说明
 * @param <T>  响应数据类型
 */
public record ApiResponse<T>(int code, T data, String msg) {

    private static final int SUCCESS_CODE = HttpStatusEnum.OK.value();
    private static final String SUCCESS_MESSAGE = "成功";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, data, SUCCESS_MESSAGE);
    }


    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(SUCCESS_CODE, null, SUCCESS_MESSAGE);
    }

    public static ApiResponse<Void> failure(int code, String message) {
        if (code < HttpStatusEnum.BAD_REQUEST.value()) {
            throw new IllegalArgumentException("失败响应码必须大于等于400");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("失败响应信息不能为空");
        }
        return new ApiResponse<>(code, null, message);
    }

    public static ApiResponse<Void> failure(HttpStatusEnum status, String message) {
        if (status == null) {
            throw new IllegalArgumentException("HTTP 状态不能为空");
        }
        return failure(status.value(), message);
    }
}
