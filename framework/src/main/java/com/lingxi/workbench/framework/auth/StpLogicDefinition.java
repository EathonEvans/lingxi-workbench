package com.lingxi.workbench.framework.auth;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.framework.auth.config.SaTokenExtendConfig;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 认证操作的统一入口。
 * <p>每个启动服务通过配置独立的 loginType，实现管理端与用户端会话隔离。</p>
 */
@Component
public class StpLogicDefinition {

    private final StpLogic stpLogic;

    public StpLogicDefinition(SaTokenExtendConfig saTokenExtendConfig) {
        String loginType = saTokenExtendConfig.getLoginType();
        if (CharSequenceUtil.isBlankOrUndefined(loginType)) {
            throw new IllegalArgumentException("loginType must not be blank");
        }
        this.stpLogic = new StpLogic(loginType);
        // 每个启动服务仅维护一种登录体系，将配置创建的认证逻辑设为当前进程默认值。
        // 业务接口使用 @SaCheckPermission 时无需重复声明 type，避免配置与注解产生双重来源。
        StpUtil.setStpLogic(this.stpLogic);
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
