package com.lingxi.workbench.auth.model.vo;

public record UserLoginVO(
        String tokenName,
        String tokenValue,
        long expiresInSeconds,
        UserProfileVO user
) {

    public record UserProfileVO(
            Long id,
            String username,
            String displayName,
            String avatarUrl,
            String email,
            String mobile
    ) {
        public UserProfileVO(Long id, String username, String displayName, String avatarUrl) {
            this(id, username, displayName, avatarUrl, null, null);
        }
    }
}
