package com.lingxi.workbench.auth.application;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.auth.domain.UserAccount;
import com.lingxi.workbench.auth.domain.UserAccountRepository;
import com.lingxi.workbench.framework.auth.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserLoginService {

    private static final String DUMMY_PASSWORD_HASH = "$2y$12$FA5DZrrOlNthZ7InIh7ETeSW/geeAMPh63WEsjBSwC52li/eZ76By";

    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;
    private final StpLogicDefinition stpLogicDefinition;


    @Transactional(readOnly = true)
    public UserLoginResult login(UserLoginCommand command) {
        String normalizedUsername = normalizeUsername(command.username());
        UserAccount account = userAccountRepository.findByUsername(normalizedUsername)
                .orElse(null);

        if (account == null) {
            passwordHasher.matches(command.password(), DUMMY_PASSWORD_HASH);
            throw AuthException.invalidCredentials();
        }

        if (!passwordHasher.matches(command.password(), account.passwordHash())) {
            throw AuthException.invalidCredentials();
        }

        if (!account.isActive()) {
            throw AuthException.accountDisabled("账号已停用，请联系管理员");
        }

        SaTokenInfo tokenInfo = stpLogicDefinition.login(account.id());
        return new UserLoginResult(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                account.id(),
                account.username(),
                account.displayName(),
                account.avatarUrl()
        );
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
