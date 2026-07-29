package com.lingxi.workbench.admin.sys.user.model.bo;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 统一系统用户业务对象，隔离数据库实体与账号业务。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysUserBO {
    /**
     * 用户主键。
     */
    private Long id;
    /**
     * 登录账号。
     */
    private String username;
    /**
     * BCrypt 密码哈希。
     */
    private String passwordHash;
    /**
     * 显示名称。
     */
    private String displayName;
    /**
     * 头像地址。
     */
    private String avatarUrl;
    /**
     * 邮箱地址。
     */
    private String email;
    /**
     * 手机号。
     */
    private String mobile;
    /**
     * 所属部门主键。
     */
    private Long departmentId;
    /**
     * 账号状态。
     */
    private SysUserStatus status;
    /**
     * 是否已开启应用端访问权限。
     */
    private Boolean applicationAccessEnabled;
    /**
     * 是否已分配至少一个有效管理角色。
     */
    private Boolean adminAccessEnabled;
    /**
     * 创建时间。
     */
    private OffsetDateTime createdAt;
    /**
     * 更新时间。
     */
    private OffsetDateTime updatedAt;

    /**
     * 判断账号是否处于启用状态。
     */
    public boolean isActive() {
        return status == SysUserStatus.ACTIVE;
    }

    /**
     * 判断账号是否已分配至少一个有效管理角色，即可访问管理后台。
     */
    public boolean canAccessAdmin() {
        return Boolean.TRUE.equals(adminAccessEnabled);
    }
}
