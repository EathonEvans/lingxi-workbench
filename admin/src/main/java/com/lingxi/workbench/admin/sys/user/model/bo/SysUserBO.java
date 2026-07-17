package com.lingxi.workbench.admin.sys.user.model.bo;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 统一系统用户业务对象，隔离数据库实体与账号业务。
 */
public record SysUserBO(
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
        /** 所属部门主键。 */
        Long departmentId,
        /** 账号状态。 */
        SysUserStatus status,
        /** 是否已开启应用端访问权限。 */
        Boolean applicationAccessEnabled,
        /** 是否已分配至少一个有效管理角色。 */
        Boolean adminAccessEnabled,
        /** 创建时间。 */
        OffsetDateTime createdAt,
        /** 更新时间。 */
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

    public boolean canAccessAdmin() {
        return Boolean.TRUE.equals(adminAccessEnabled);
    }
}
