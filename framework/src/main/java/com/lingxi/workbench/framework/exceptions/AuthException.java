package com.lingxi.workbench.framework.exceptions;

/**
 * Admin 和 Application 共用的认证异常。
 */
public final class AuthException extends BusinessException {

    private AuthException(int httpStatus, int code, String message) {
        super(httpStatus, code, message);
    }

    public static AuthException invalidCredentials() {
        return new AuthException(401, 401, "用户名或密码错误");
    }

    public static AuthException accountDisabled(String message) {
        return new AuthException(403, 403, message);
    }

    public static AuthException accountNotFound() {
        return new AuthException(401, 401, "登录账号不存在，请重新登录");
    }
}
