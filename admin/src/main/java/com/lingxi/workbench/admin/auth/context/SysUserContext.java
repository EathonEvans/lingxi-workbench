package com.lingxi.workbench.admin.auth.context;

import com.lingxi.workbench.framework.context.LoginUser;

import java.util.Objects;

/**
 * 当前管理端请求的登录用户上下文。
 */
public final class SysUserContext {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private SysUserContext() {}

    public static void set(LoginUser loginUser) {
        CONTEXT.set(loginUser);
    }

    public static LoginUser get() {
        LoginUser loginUser = CONTEXT.get();
        if (Objects.isNull(loginUser)) {
            throw new IllegalStateException("当前请求不存在管理员上下文");
        }
        return loginUser;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
