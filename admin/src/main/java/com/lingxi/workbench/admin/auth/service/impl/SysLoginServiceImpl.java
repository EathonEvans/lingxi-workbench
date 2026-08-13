package com.lingxi.workbench.admin.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.auth.model.request.SysLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;
import com.lingxi.workbench.admin.auth.service.SysLoginService;
import com.lingxi.workbench.admin.file.service.AvatarUrlService;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.sys.user.repo.SysUserRepo;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.login.LoginFailureGuard;
import com.lingxi.workbench.framework.auth.password.BCryptPasswordHasher;
import com.lingxi.workbench.framework.exceptions.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * 系统用户密码登录实现。
 *
 * <p>账号不存在时仍执行一次固定 BCrypt 哈希校验，降低通过响应耗时探测有效账号的风险。</p>
 */
@Service
@RequiredArgsConstructor
public class SysLoginServiceImpl implements SysLoginService {

    /** 与 sa-token.login-type 保持一致，用于防爆破按端隔离计数。 */
    private static final String LOGIN_TYPE = "admin";

    private final SysUserRepo sysUserRepo;
    private final SysUserConverter sysUserConverter;
    private final BCryptPasswordHasher passwordHasher;
    private final StpLogicDefinition stpLogicDefinition;
    private final AvatarUrlService avatarUrlService;
    private final LoginFailureGuard loginFailureGuard;

    /**
     * 执行账号密码登录流程：校验账号存在性、密码匹配、账号状态，登录成功后签发令牌。
     *
     * <p>账号不存在时仍执行一次 BCrypt 哈希校验，防止通过响应耗时推断有效账号。
     * 连续失败超过阈值时临时锁定，防止口令爆破。</p>
     *
     * @param request 登录请求参数
     * @return 登录响应
     */
    @Override
    @Transactional(readOnly = true)
    public SysLoginVO login(SysLoginRequest request) {
        String username = normalizeUsername(request.username());
        if (loginFailureGuard.isLocked(LOGIN_TYPE, username)) {
            throw AuthException.loginLocked("登录失败次数过多，账号已被临时锁定，请稍后再试");
        }
        Optional<SysUserBO> userOpt = sysUserRepo.findByUsername(username);

        if (userOpt.isEmpty()) {
            // 账号不存在时执行常量时间虚拟比对，防止通过响应耗时探测有效账号
            passwordHasher.timingSafeDummyMatch(request.password());
            loginFailureGuard.recordFailure(LOGIN_TYPE, username);
            throw AuthException.invalidCredentials();
        }

        SysUserBO user = userOpt.get();

        // 密码校验
        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            loginFailureGuard.recordFailure(LOGIN_TYPE, username);
            throw AuthException.invalidCredentials();
        }

        // 账号状态校验
        if (!user.isActive()) {
            throw AuthException.userDisabled("系统用户已停用");
        }
        // 管理端访问权限校验
        if (!user.canAccessAdmin()) {
            throw AuthException.userDisabled("账号未开通管理端访问权限");
        }

        // 登录成功，清除历史失败计数
        loginFailureGuard.clear(LOGIN_TYPE, username);

        // 通过 Sa-Token 签发登录令牌
        SaTokenInfo tokenInfo = stpLogicDefinition.login(user.getId());
        AvatarUrlService.AvatarUrl avatar = resolveAvatar(user.getAvatarUrl());
        SysLoginVO.SysProfileVO profile = sysUserConverter.toProfileVO(
                user, avatar.url(), avatar.objectKey());
        return new SysLoginVO(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                profile
        );
    }

    /** 解析登录响应所需的头像地址，空头像不访问对象存储。 */
    private AvatarUrlService.AvatarUrl resolveAvatar(String storedValue) {
        return CharSequenceUtil.isBlank(storedValue)
                ? new AvatarUrlService.AvatarUrl(null, null)
                : avatarUrlService.resolve(storedValue);
    }

    /**
     * 标准化登录账号：去除首尾空白并转为小写。
     *
     * @param username 原始账号名
     * @return 标准化后的账号名
     */
    private static String normalizeUsername(String username) {
        if (CharSequenceUtil.isBlank(username)) {
            throw AuthException.invalidCredentials();
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
