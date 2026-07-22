package com.lingxi.workbench.framework.context;

/**
 * 当前请求的登录用户上下文，基于 {@link ThreadLocal} 隔离各请求线程。
 *
 * <p>在认证拦截器中设置，请求结束后清除，供 Controller 和 Service 层获取当前登录用户。</p>
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 将当前登录用户绑定到请求线程。
     *
     * @param loginUser 登录用户信息
     */
    public static void set(LoginUser loginUser) {
        CONTEXT.set(loginUser);
    }

    /**
     * 获取当前请求的登录用户，未设置时抛出异常。
     *
     * @return 当前登录用户
     * @throws IllegalStateException 当前请求不存在用户上下文
     */
    public static LoginUser get() {
        LoginUser loginUser = CONTEXT.get();
        if (loginUser == null) {
            throw new IllegalStateException("当前请求不存在用户上下文");
        }
        return loginUser;
    }

    /**
     * 获取当前登录用户的 ID。
     *
     * @return 用户主键
     * @throws IllegalStateException 当前请求不存在用户上下文
     */
    public static Long userId() {
        return get().id();
    }

    /**
     * 清除当前线程的用户上下文，防止线程复用污染和内存泄漏。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
