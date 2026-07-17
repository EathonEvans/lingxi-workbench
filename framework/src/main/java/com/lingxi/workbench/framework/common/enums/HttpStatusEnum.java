package com.lingxi.workbench.framework.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 项目统一使用的关键 HTTP 状态。
 *
 * <p>这里只收录系统实际使用的标准 HTTP 状态，不允许在业务模块中自行声明状态值。
 * 具体业务结果仍由 {@code ApiResponse.code} 表达。</p>
 */
@Getter
@RequiredArgsConstructor
public enum HttpStatusEnum {

    OK(200, "请求成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源状态冲突"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误");

    /** 标准 HTTP 状态码。 */
    private final int code;
    /** 中文说明。 */
    private final String describe;
}
