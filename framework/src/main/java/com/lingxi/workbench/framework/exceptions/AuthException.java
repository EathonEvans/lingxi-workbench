package com.lingxi.workbench.framework.exceptions;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;

/**
 * 管理端和应用端共用的认证异常。
 */
public final class AuthException extends BusinessException {

    private AuthException(HttpStatusEnum httpStatus, String message) {
        super(httpStatus, message);
    }

    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatusEnum.UNAUTHORIZED, "用户名或密码错误");
    }

    public static AuthException userDisabled(String message) {
        return new AuthException(HttpStatusEnum.FORBIDDEN, message);
    }

    public static AuthException userNotFound() {
        return new AuthException(HttpStatusEnum.UNAUTHORIZED, "登录账号不存在，请重新登录");
    }
}
