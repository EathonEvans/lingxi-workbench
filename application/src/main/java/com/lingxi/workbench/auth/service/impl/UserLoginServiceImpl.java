package com.lingxi.workbench.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.model.converter.UserConverter;
import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import com.lingxi.workbench.auth.repo.UserRepo;
import com.lingxi.workbench.auth.service.UserLoginService;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    private static final String DUMMY_PASSWORD_HASH = "$2y$12$FA5DZrrOlNthZ7InIh7ETeSW/geeAMPh63WEsjBSwC52li/eZ76By";

    private final UserRepo userRepo;
    private final UserConverter userConverter;
    private final PasswordHasher passwordHasher;
    private final StpLogicDefinition stpLogicDefinition;

    @Override
    @Transactional(readOnly = true)
    public UserLoginVO login(UserLoginRequest request) {
        Optional<UserBO> userOpt = userRepo.findByUsername(normalizeUsername(request.username()));

        if (userOpt.isEmpty()) {
            passwordHasher.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw AuthException.invalidCredentials();
        }

        UserBO user = userOpt.get();

        if (!passwordHasher.matches(request.password(), user.passwordHash())) {
            throw AuthException.invalidCredentials();
        }

        if (!user.isActive()) {
            throw AuthException.userDisabled("账号已停用，请联系管理员");
        }

        if (!user.canAccessApplication()) {
            throw AuthException.userDisabled("账号未开通应用端访问权限，请联系管理员");
        }

        SaTokenInfo tokenInfo = stpLogicDefinition.login(user.id());
        return new UserLoginVO(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                userConverter.toProfileVO(user)
        );
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
