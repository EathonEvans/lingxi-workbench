package com.lingxi.workbench.admin.sys.user.model.vo;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 系统用户列表响应，不返回密码哈希等敏感信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysUserVO {
    /**
     * 用户主键。
     */
    private Long id;
    /**
     * 登录账号。
     */
    private String username;
    /**
     * 显示名称。
     */
    private String displayName;
    /**
     * 头像地址（带签名的临时访问URL，用于前端直接渲染）。
     */
    private String avatarUrl;
    /**
     * 头像对象存储 key（用于更新时回传服务端）。
     */
    private String avatarObjectKey;
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
}