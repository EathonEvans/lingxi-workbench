package com.lingxi.workbench.admin.sys.user.model.vo;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;

import java.time.OffsetDateTime;

/** 系统用户列表响应，不返回密码哈希等敏感信息。 */
public record SysUserVO(
        /** 用户主键。 */
        Long id,
        /** 登录账号。 */
        String username,
        /** 显示名称。 */
        String displayName,
        /** 头像地址（带签名的临时访问URL，用于前端直接渲染）。 */
        String avatarUrl,
        /** 头像对象存储 key（用于更新时回传服务端）。 */
        String avatarObjectKey,
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
}
