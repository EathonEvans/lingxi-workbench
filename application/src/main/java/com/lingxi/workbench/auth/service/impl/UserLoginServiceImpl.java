package com.lingxi.workbench.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.auth.model.bo.UserAccountBO;
import com.lingxi.workbench.auth.model.converter.UserAccountConverter;
import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import com.lingxi.workbench.auth.repo.UserAccountRepo;
import com.lingxi.workbench.auth.service.UserLoginService;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    private static final String DUMMY_PASSWORD_HASH = "$2y$12$FA5DZrrOlNthZ7InIh7ETeSW/geeAMPh63WEsjBSwC52li/eZ76By";

    private final UserAccountRepo userAccountRepo;
    private final UserAccountConverter userAccountConverter;
    private final PasswordHasher passwordHasher;
    private final StpLogicDefinition stpLogicDefinition;

    @Override
    @Transactional(readOnly = true)
    public UserLoginVO login(UserLoginRequest request) {
        UserAccountBO account = userAccountRepo.findByUsername(normalizeUsername(request.username()))
                .orElse(null);

        if (account == null) {
            passwordHasher.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw AuthException.invalidCredentials();
        }

        if (!passwordHasher.matches(request.password(), account.passwordHash())) {
            throw AuthException.invalidCredentials();
        }

        if (!account.isActive()) {
            throw AuthException.accountDisabled("账号已停用，请联系管理员");
        }

        SaTokenInfo tokenInfo = stpLogicDefinition.login(account.id());
        return new UserLoginVO(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                userAccountConverter.toProfileVO(account)
        );
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
