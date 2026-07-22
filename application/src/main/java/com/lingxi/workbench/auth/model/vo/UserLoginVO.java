package com.lingxi.workbench.auth.model.vo;

/**
 * 应用端用户登录响应，包含 Sa-Token 令牌和用户基础资料。
 */
public record UserLoginVO(
        /** Sa-Token 令牌名称。 */
        String tokenName,
        /** Sa-Token 令牌值。 */
        String tokenValue,
        /** 令牌过期时间（秒）。 */
        long expiresInSeconds,
        /** 用户基础资料。 */
        UserProfileVO user
) {

    /**
     * 用户基础资料，不包含密码等敏感字段。
     */
    public record UserProfileVO(
            /** 用户主键。 */
            Long id,
            /** 登录账号。 */
            String username,
            /** 显示名称。 */
            String displayName,
            /** 头像地址。 */
            String avatarUrl,
            /** 邮箱地址。 */
            String email,
            /** 手机号。 */
            String mobile
    ) {
        /**
         * 兼容仅需基础身份信息的构造方式，email 和 mobile 默认为 null。
         */
        public UserProfileVO(Long id, String username, String displayName, String avatarUrl) {
            this(id, username, displayName, avatarUrl, null, null);
        }
    }
}
