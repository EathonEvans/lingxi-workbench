package com.lingxi.workbench.admin.auth.model.vo;

/**
 * 管理员登录响应，包含 Sa-Token 令牌和管理员基础资料。
 */
public record SysLoginVO(
        String tokenName,
        String tokenValue,
        long expiresInSeconds,
        SysProfileVO user
) {

    /** 管理员基础资料，不包含密码哈希等敏感字段。 */
    public record SysProfileVO(
            Long id,
            String username,
            String displayName,
            String avatarUrl,
            Long departmentId,
            String email,
            String mobile
    ) {
        public SysProfileVO(Long id, String username, String displayName, String avatarUrl, Long departmentId) {
            this(id, username, displayName, avatarUrl, departmentId, null, null);
        }
    }
}
