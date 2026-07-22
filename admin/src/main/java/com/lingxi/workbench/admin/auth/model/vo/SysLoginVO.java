package com.lingxi.workbench.admin.auth.model.vo;

/**
 * 管理员登录响应，包含 Sa-Token 令牌和管理员基础资料。
 */
public record SysLoginVO(
        /** Sa-Token 令牌名称。 */
        String tokenName,
        /** Sa-Token 令牌值。 */
        String tokenValue,
        /** 令牌过期时间（秒）。 */
        long expiresInSeconds,
        /** 管理员基础资料。 */
        SysProfileVO user
) {

    /** 管理员基础资料，不包含密码哈希等敏感字段。 */
    public record SysProfileVO(
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
            /** 所属部门主键。 */
            Long departmentId,
            /** 邮箱地址。 */
            String email,
            /** 手机号。 */
            String mobile
    ) {
        public SysProfileVO(Long id, String username, String displayName, String avatarUrl, Long departmentId) {
            this(id, username, displayName, avatarUrl, null, departmentId, null, null);
        }
    }
}
