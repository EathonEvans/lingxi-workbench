package com.lingxi.workbench.admin.auth.model.vo;

import com.lingxi.workbench.admin.auth.common.enums.SysUserStatus;

import java.time.OffsetDateTime;

/** 系统用户列表响应，不返回密码哈希等敏感信息。 */
public record SysUserVO(
        Long id,
        String username,
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
}
