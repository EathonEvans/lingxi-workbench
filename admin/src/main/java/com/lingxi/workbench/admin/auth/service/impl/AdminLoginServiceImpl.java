package com.lingxi.workbench.admin.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.admin.auth.model.bo.AdminAccountBO;
import com.lingxi.workbench.admin.auth.model.converter.AdminAccountConverter;
import com.lingxi.workbench.admin.auth.model.request.AdminLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.AdminLoginVO;
import com.lingxi.workbench.admin.auth.repo.AdminAccountRepo;
import com.lingxi.workbench.admin.auth.service.AdminLoginService;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 管理员账号密码登录实现。
 *
 * <p>账号不存在时仍执行一次固定 BCrypt 哈希校验，降低通过响应耗时探测有效账号的风险。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminLoginServiceImpl implements AdminLoginService {

    private static final String DUMMY_PASSWORD_HASH = "$2y$12$FA5DZrrOlNthZ7InIh7ETeSW/geeAMPh63WEsjBSwC52li/eZ76By";

    private final AdminAccountRepo adminAccountRepo;
    private final AdminAccountConverter adminAccountConverter;
    private final PasswordHasher passwordHasher;
    private final StpLogicDefinition stpLogicDefinition;

    @Override
    @Transactional(readOnly = true)
    public AdminLoginVO login(AdminLoginRequest request) {
        AdminAccountBO account = adminAccountRepo.findByUsername(normalizeUsername(request.username()))
                .orElse(null);

        if (account == null) {
            passwordHasher.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw AuthException.invalidCredentials();
        }

        if (!passwordHasher.matches(request.password(), account.passwordHash())) {
            throw AuthException.invalidCredentials();
        }

        if (!account.isActive()) {
            throw AuthException.accountDisabled("管理员账号已停用");
        }

        SaTokenInfo tokenInfo = stpLogicDefinition.login(account.id());
        return new AdminLoginVO(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                adminAccountConverter.toProfileVO(account)
        );
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
