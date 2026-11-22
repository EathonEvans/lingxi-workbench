package com.lingxi.workbench.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.converter.UserConverter;
import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import com.lingxi.workbench.auth.repo.UserRepo;
import com.lingxi.workbench.auth.service.UserAvatarUrlService;
import com.lingxi.workbench.auth.service.UserLoginService;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.login.LoginFailureGuard;
import com.lingxi.workbench.framework.auth.password.BCryptPasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * 应用端用户登录服务实现，负责登录全流程的校验与令牌签发。
 */
@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    /** 与 sa-token.login-type 保持一致，用于防爆破按端隔离计数。 */
    private static final String LOGIN_TYPE = "user";

    private final UserRepo userRepo;
    private final UserConverter userConverter;
    private final BCryptPasswordHasher passwordHasher;
    private final StpLogicDefinition stpLogicDefinition;
    private final UserAvatarUrlService userAvatarUrlService;
    private final LoginFailureGuard loginFailureGuard;

    /**
     * 执行登录流程：
     * <ol>
     *   <li>用户名标准化处理（去空格、小写）；</li>
     *   <li>查询用户，不存在时仍执行密码比对以防御时序攻击；</li>
     *   <li>验证密码；</li>
     *   <li>校验账号启用状态与应用端访问权限；</li>
     *   <li>签发 Sa-Token 并返回令牌与用户资料。</li>
     * </ol>
     *
     * @param request 登录请求参数
     * @return 包含令牌与用户资料的登录响应
     */
    @Override
    @Transactional(readOnly = true)
    public UserLoginVO login(UserLoginRequest request) {
        String username = normalizeUsername(request.username());
        if (loginFailureGuard.isLocked(LOGIN_TYPE, username)) {
            throw AuthException.loginLocked("登录失败次数过多，账号已被临时锁定，请稍后再试");
        }
        Optional<UserBO> userOpt = userRepo.findByUsername(username);

        if (userOpt.isEmpty()) {
            // 用户不存在时执行常量时间虚拟比对，防止通过响应时间差异枚举有效账号
            passwordHasher.timingSafeDummyMatch(request.password());
            loginFailureGuard.recordFailure(LOGIN_TYPE, username);
            throw AuthException.invalidCredentials();
        }

        UserBO user = userOpt.get();

        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            loginFailureGuard.recordFailure(LOGIN_TYPE, username);
            throw AuthException.invalidCredentials();
        }

        if (!user.isActive()) {
            throw AuthException.userDisabled("账号已停用，请联系管理员");
        }

        if (!user.canAccessApplication()) {
            throw AuthException.userDisabled("账号未开通应用端访问权限，请联系管理员");
        }

        // 登录成功，清除历史失败计数
        loginFailureGuard.clear(LOGIN_TYPE, username);

        // 登录成功后签发 Sa-Token，并将头像对象Key转换为浏览器可访问的临时地址
        SaTokenInfo tokenInfo = stpLogicDefinition.login(user.getId());
        UserLoginVO.UserProfileVO profile = userConverter.toProfileVO(
                user, userAvatarUrlService.resolve(user.getAvatarUrl()));
        return new UserLoginVO(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                profile
        );
    }

    /**
     * 标准化用户名：去除首尾空格并转为小写。
     *
     * @param username 原始用户名
     * @return 标准化后的用户名
     */
    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
