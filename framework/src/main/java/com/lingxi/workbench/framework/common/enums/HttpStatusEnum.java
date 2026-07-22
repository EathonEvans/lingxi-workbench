package com.lingxi.workbench.framework.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 项目统一使用的关键 HTTP 状态枚举。
 *
 * <p>这里只收录系统实际使用的标准 HTTP 状态，不允许在业务模块中自行声明状态值。
 * 具体业务结果仍由 {@link ApiResponse#code} 表达。</p>
 *
 * <p>每个枚举包含标准 HTTP 状态码（{@code code}）和中文说明（{@code describe}），
 * {@code code} 用于 HTTP 响应和外部契约，{@code describe} 仅用于日志和展示。</p>
 */
@Getter
@RequiredArgsConstructor
public enum HttpStatusEnum {

    /** 请求成功 */
    OK(200, "请求成功"),
    /** 请求参数错误 */
    BAD_REQUEST(400, "请求参数错误"),
    /** 未认证，需要登录 */
    UNAUTHORIZED(401, "未认证"),
    /** 无访问权限 */
    FORBIDDEN(403, "无访问权限"),
    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 资源状态冲突 */
    CONFLICT(409, "资源状态冲突"),
    /** 服务器内部错误 */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误");

    /** 标准 HTTP 状态码。 */
    private final int code;
    /** 中文说明，仅用于日志和展示，不参与业务判断。 */
    private final String describe;
}
