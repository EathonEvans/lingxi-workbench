package com.lingxi.workbench.admin.auth.model.vo;

/**
 * 管理员登录响应，包含 Sa-Token 令牌和管理员基础资料。
 */
public record AdminLoginVO(
        String tokenName,
        String tokenValue,
        long expiresInSeconds,
        AdminProfileVO admin
) {

    /** 管理员基础资料，不包含密码哈希等敏感字段。 */
    public record AdminProfileVO(
            Long id,
            String username,
            String displayName,
            String avatarUrl,
            Long departmentId
    ) {
    }
}
