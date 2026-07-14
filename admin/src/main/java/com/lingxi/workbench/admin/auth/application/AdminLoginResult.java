package com.lingxi.workbench.admin.auth.application;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.lingxi.workbench.admin.auth.domain.AdminAccount;

public record AdminLoginResult(
        String tokenName,
        String tokenValue,
        long expiresInSeconds,
        Long adminId,
        String username,
        String displayName,
        String avatarUrl
) {

    public AdminLoginResult(SaTokenInfo tokenInfo, AdminAccount account) {
        this(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                account.id(),
                account.username(),
                account.displayName(),
                account.avatarUrl()
        );
    }
}
