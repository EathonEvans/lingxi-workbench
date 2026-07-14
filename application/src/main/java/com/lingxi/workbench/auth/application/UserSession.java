package com.lingxi.workbench.auth.application;

public record UserSession(
        String tokenName,
        String tokenValue,
        long expiresInSeconds
) {
}
