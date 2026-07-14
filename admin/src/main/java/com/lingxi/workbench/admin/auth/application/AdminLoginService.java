package com.lingxi.workbench.admin.auth.application;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.admin.auth.domain.AdminAccount;
import com.lingxi.workbench.admin.auth.domain.AdminAccountRepository;
import com.lingxi.workbench.framework.auth.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminLoginService {

    private static final String DUMMY_PASSWORD_HASH = "$2y$12$FA5DZrrOlNthZ7InIh7ETeSW/geeAMPh63WEsjBSwC52li/eZ76By";

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordHasher passwordHasher;
    private final StpLogicDefinition stpLogicDefinition;


    @Transactional(readOnly = true)
    public AdminLoginResult login(AdminLoginCommand command) {
        // 查询用户
        AdminAccount account = adminAccountRepository.findByUsername(normalizeUsername(command.username())).orElse(null);

        if (account == null) {
            passwordHasher.matches(command.password(), DUMMY_PASSWORD_HASH);
            throw AuthException.invalidCredentials();
        }
        // 密码比对
        if (!passwordHasher.matches(command.password(), account.passwordHash())) {
            throw AuthException.invalidCredentials();
        }

        if (!account.isActive()) {
            throw AuthException.accountDisabled("管理员账号已停用");
        }

        SaTokenInfo tokenInfo = stpLogicDefinition.login(account.id());

        return new AdminLoginResult(tokenInfo, account);
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
