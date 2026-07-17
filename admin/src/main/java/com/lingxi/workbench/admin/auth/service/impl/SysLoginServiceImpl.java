package com.lingxi.workbench.admin.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.model.converter.SysUserConverter;
import com.lingxi.workbench.admin.auth.model.request.SysLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;
import com.lingxi.workbench.admin.sys.user.repo.SysUserRepo;
import com.lingxi.workbench.admin.auth.service.SysLoginService;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 系统用户密码登录实现。
 *
 * <p>账号不存在时仍执行一次固定 BCrypt 哈希校验，降低通过响应耗时探测有效账号的风险。</p>
 */
@Service
@RequiredArgsConstructor
public class SysLoginServiceImpl implements SysLoginService {

    private static final String DUMMY_PASSWORD_HASH = "$2y$12$FA5DZrrOlNthZ7InIh7ETeSW/geeAMPh63WEsjBSwC52li/eZ76By";

    private final SysUserRepo sysUserRepo;
    private final SysUserConverter sysUserConverter;
    private final PasswordHasher passwordHasher;
    private final StpLogicDefinition stpLogicDefinition;

    @Override
    @Transactional(readOnly = true)
    public SysLoginVO login(SysLoginRequest request) {
        SysUserBO user = sysUserRepo.findByUsername(normalizeUsername(request.username()))
                .orElse(null);

        if (user == null) {
            passwordHasher.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw AuthException.invalidCredentials();
        }

        if (!passwordHasher.matches(request.password(), user.passwordHash())) {
            throw AuthException.invalidCredentials();
        }

        if (!user.isActive()) {
            throw AuthException.userDisabled("系统用户已停用");
        }
        if (!user.canAccessAdmin()) {
            throw AuthException.userDisabled("账号未开通管理端访问权限");
        }

        SaTokenInfo tokenInfo = stpLogicDefinition.login(user.id());
        return new SysLoginVO(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                sysUserConverter.toProfileVO(user)
        );
    }

    private static String normalizeUsername(String username) {
        if (CharSequenceUtil.isBlank(username)) {
            throw AuthException.invalidCredentials();
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
