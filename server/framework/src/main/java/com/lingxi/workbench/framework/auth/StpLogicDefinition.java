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

    /** 退出当前会话的登录状态。 */
    public void logout() {
        stpLogic.logout();
    }

    /**
     * 根据用户 ID 退出指定会话的登录状态。
     *
     * @param userId 用户主键
     */
    public void logoutByLoginId(Long userId) {
        stpLogic.logout(userId);
    }

    /**
     * 根据用户 ID 踢出指定会话，使其令牌失效。
     *
     * @param userId 用户主键
     */
    public void kickoutByLoginId(Long userId) {
        stpLogic.kickout(userId);
    }

    /** 检查当前请求是否已登录，未登录时抛出异常。 */
    public void checkLogin() {
        stpLogic.checkLogin();
    }

    /**
     * 判断当前请求是否已登录。
     *
     * @return 已登录返回 {@code true}
     */
    public boolean isLogin() {
        return stpLogic.isLogin();
    }

    /**
     * 获取当前登录用户的 ID。
     *
     * @return 用户主键
     */
    public long getLoginIdAsLong() {
        return stpLogic.getLoginIdAsLong();
    }

    /**
     * 检查当前登录用户是否拥有指定权限，无权限时抛出异常。
     *
     * @param permission 权限编码
     */
    public void checkPermission(String permission) {
        stpLogic.checkPermission(permission);
    }

    /**
     * 检查当前登录用户是否拥有指定角色，无角色时抛出异常。
     *
     * @param role 角色编码
     */
    public void checkRole(String role) {
        stpLogic.checkRole(role);
    }

    /**
     * 获取当前服务的登录体系标识。
     *
     * @return 登录类型字符串
     */
    public String getLoginType() {
        return stpLogic.getLoginType();
    }
}
