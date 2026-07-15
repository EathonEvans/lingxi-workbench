package com.lingxi.workbench.auth.context;

import com.lingxi.workbench.framework.context.LoginUser;

/**
 * 当前用户端请求的登录用户上下文。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        CONTEXT.set(loginUser);
    }

    public static LoginUser get() {
        LoginUser loginUser = CONTEXT.get();
        if (loginUser == null) {
            throw new IllegalStateException("当前请求不存在用户上下文");
        }
        return loginUser;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
