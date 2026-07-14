package com.lingxi.workbench.framework.auth.logic;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;

/**
 *  sa令牌帐户领域的通用外观。
 *  <p>每个具体的帐户域必须提供稳定的登录类型。
 *  Sa-token使用登录类型作为其会话名称空间的一部分，
 *  因此它不能从可能在重构期间更改的Java枚举名称派生
 */
public abstract class StpLogicDefinition {

    private final StpLogic stpLogic;

    protected StpLogicDefinition(String loginType) {
        if (loginType == null || loginType.isBlank()) {
            throw new IllegalArgumentException("loginType must not be blank");
        }
        this.stpLogic = new StpLogic(loginType);
    }

    /**
     * 登录到此帐户领域并返回为当前请求创建的令牌。
     */
    public SaTokenInfo login(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        stpLogic.login(userId);
        return stpLogic.getTokenInfo();
    }

    public void logout() {
        stpLogic.logout();
    }

    public void logoutByLoginId(Long userId) {
        stpLogic.logout(userId);
    }

    public void kickoutByLoginId(Long userId) {
        stpLogic.kickout(userId);
    }

    public void checkLogin() {
        stpLogic.checkLogin();
    }

    public boolean isLogin() {
        return stpLogic.isLogin();
    }

    public long getLoginIdAsLong() {
        return stpLogic.getLoginIdAsLong();
    }

    public void checkPermission(String permission) {
        stpLogic.checkPermission(permission);
    }

    public void checkRole(String role) {
        stpLogic.checkRole(role);
    }

    public String getLoginType() {
        return stpLogic.getLoginType();
    }
}
