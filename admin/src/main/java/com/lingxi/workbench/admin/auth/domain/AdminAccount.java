package com.lingxi.workbench.admin.auth.domain;

import java.util.Objects;

public record AdminAccount(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        String avatarUrl,
        AdminAccountStatus status
) {

    public AdminAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public boolean isActive() {
        return status == AdminAccountStatus.ACTIVE;
    }
}
