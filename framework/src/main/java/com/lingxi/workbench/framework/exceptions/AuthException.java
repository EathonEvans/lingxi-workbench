package com.lingxi.workbench.framework.exceptions;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

/**
 * 管理端和应用端共用的认证异常。
 *
 * <p>继承自 {@link BusinessException}，统一使用静态工厂方法创建语义明确的认证异常实例，
 * 禁止直接通过构造器实例化。</p>
 */
public final class AuthException extends BusinessException {

    /**
     * 私有构造器，仅通过静态工厂方法创建。
     *
     * @param httpStatus HTTP 错误状态
     * @param message    错误描述
     */
    private AuthException(HttpStatusEnum httpStatus, String message) {
        super(httpStatus, message);
    }

    /**
     * 凭证无效异常，通常用于账密登录失败。
     *
     * @return 认证异常，HTTP 状态 401
     */
    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatusEnum.UNAUTHORIZED, "用户名或密码错误");
    }

    /**
     * 用户被禁用异常。
     *
     * @param message 禁用原因描述
     * @return 认证异常，HTTP 状态 403
     */
    public static AuthException userDisabled(String message) {
        return new AuthException(HttpStatusEnum.FORBIDDEN, message);
    }

    /**
     * 用户不存在异常，通常用于 Token 解析后的用户查找失败。
     *
     * @return 认证异常，HTTP 状态 401
     */
    public static AuthException userNotFound() {
        return new AuthException(HttpStatusEnum.UNAUTHORIZED, "登录账号不存在，请重新登录");
    }
}
