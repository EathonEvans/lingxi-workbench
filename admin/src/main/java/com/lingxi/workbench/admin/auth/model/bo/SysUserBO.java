package com.lingxi.workbench.admin.auth.model.bo;

import com.lingxi.workbench.admin.auth.common.enums.SysUserStatus;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 管理员认证业务对象，隔离数据库实体与登录业务。
 */
public record SysUserBO(
        Long id,
        String username,
        String passwordHash,
        String displayName,
        String avatarUrl,
        String email,
        String mobile,
        Long departmentId,
        SysUserStatus status,
        Boolean applicationAccessEnabled,
        Boolean adminAccessEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public SysUserBO {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public boolean isActive() {
        return status == SysUserStatus.ACTIVE;
    }

    /** 兼容认证场景仅需要账号基础资料的构造方式。 */
    public SysUserBO(Long id,
                          String username,
                          String passwordHash,
                          String displayName,
                          String avatarUrl,
                          Long departmentId,
                          SysUserStatus status) {
        this(id, username, passwordHash, displayName, avatarUrl, null, null, departmentId, status,
                false, false, null, null);
    }
}
