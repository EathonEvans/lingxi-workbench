package com.lingxi.workbench.auth.application;

public record UserLoginResult(
        String tokenName,
        String tokenValue,
        long expiresInSeconds,
        Long userId,
        String username,
        String displayName,
        String avatarUrl
) {
}
