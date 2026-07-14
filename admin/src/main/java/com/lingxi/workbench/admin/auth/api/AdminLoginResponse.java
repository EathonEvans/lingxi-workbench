package com.lingxi.workbench.admin.auth.api;

import com.lingxi.workbench.admin.auth.application.AdminLoginResult;

public record AdminLoginResponse(String tokenName, String tokenValue, long expiresInSeconds, AdminProfile admin) {

    public static AdminLoginResponse from(AdminLoginResult result) {
        return new AdminLoginResponse(result.tokenName(), result.tokenValue(), result.expiresInSeconds(), new AdminProfile(result.adminId(), result.username(), result.displayName(), result.avatarUrl()));
    }

    public record AdminProfile(Long id, String username, String displayName, String avatarUrl) {
    }
}
