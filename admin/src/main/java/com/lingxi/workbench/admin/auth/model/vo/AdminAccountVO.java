package com.lingxi.workbench.admin.auth.model.vo;

import com.lingxi.workbench.admin.auth.common.enums.AdminAccountStatus;

import java.time.OffsetDateTime;

/** 管理员账号列表响应，不返回密码哈希等敏感信息。 */
public record AdminAccountVO(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        Long departmentId,
        AdminAccountStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
