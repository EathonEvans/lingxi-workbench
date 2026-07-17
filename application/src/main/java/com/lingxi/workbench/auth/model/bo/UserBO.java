package com.lingxi.workbench.auth.model.bo;

import com.lingxi.workbench.auth.common.enums.UserStatus;

import java.util.Objects;

/**
 * 应用端用户业务对象，隔离数据库实体与认证业务。
 */
public record UserBO(
        /** 用户主键。 */
        Long id,
        /** 登录账号。 */
        String username,
        /** BCrypt 密码哈希。 */
        String passwordHash,
        /** 显示名称。 */
        String displayName,
        /** 头像地址。 */
        String avatarUrl,
        /** 邮箱地址。 */
        String email,
        /** 手机号。 */
        String mobile,
        /** 账号状态。 */
        UserStatus status,
        /** 是否已开启应用端访问权限。 */
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
