package com.lingxi.workbench.auth.api;

import com.lingxi.workbench.auth.application.UserLoginResult;

public record UserLoginResponse(
        String tokenName,
        String tokenValue,
        long expiresInSeconds,
        UserProfile user
) {

    public static UserLoginResponse from(UserLoginResult result) {
        return new UserLoginResponse(
                result.tokenName(),
                result.tokenValue(),
                result.expiresInSeconds(),
                new UserProfile(
                        result.userId(),
                        result.username(),
                        result.displayName(),
                        result.avatarUrl()
                )
        );
    }

    public record UserProfile(
            Long id,
            String username,
            String displayName,
            String avatarUrl
    ) {
    }
}
