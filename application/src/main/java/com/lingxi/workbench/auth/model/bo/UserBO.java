package com.lingxi.workbench.auth.model.bo;

import com.lingxi.workbench.auth.common.enums.UserStatus;

import java.util.Objects;

public record UserBO(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        String avatarUrl,
        String email,
        String mobile,
        UserStatus status,
        Boolean applicationAccessEnabled
) {

    public UserBO {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean canAccessApplication() {
        return Boolean.TRUE.equals(applicationAccessEnabled);
    }

    /** 兼容认证测试和只需要基础账号资料的构造方式。 */
    public UserBO(Long id, String username, String passwordHash, String displayName,
                         String avatarUrl, UserStatus status) {
        this(id, username, passwordHash, displayName, avatarUrl, null, null, status, true);
    }
}
