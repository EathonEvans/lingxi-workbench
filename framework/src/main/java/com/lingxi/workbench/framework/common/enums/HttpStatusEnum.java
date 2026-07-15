package com.lingxi.workbench.framework.common.enums;

/**
 * 项目统一使用的关键 HTTP 状态。
 *
 * <p>这里只收录系统实际使用的标准 HTTP 状态，不允许在业务模块中自行声明状态值。
 * 具体业务结果仍由 {@code ApiResponse.code} 表达。</p>
 */
public enum HttpStatusEnum {

    OK(200),
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    INTERNAL_SERVER_ERROR(500);

    private final int value;

    HttpStatusEnum(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
