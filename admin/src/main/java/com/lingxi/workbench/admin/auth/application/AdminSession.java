package com.lingxi.workbench.admin.auth.application;

public record AdminSession(
        String tokenName,
        String tokenValue,
        long expiresInSeconds
) {
}
