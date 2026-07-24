package com.lingxi.workbench.auth.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用端用户登录响应，包含 Sa-Token 令牌和用户基础资料。
 *
 * @param tokenName        Sa-Token 令牌名称
 * @param tokenValue       Sa-Token 令牌值
 * @param expiresInSeconds 令牌过期时间（秒）
 * @param user             用户基础资料
 */
public record UserLoginVO(
        String tokenName,
        String tokenValue,
        long expiresInSeconds,
        UserProfileVO user
) {

    /**
     * 用户基础资料，不包含密码等敏感字段。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProfileVO {
        /** 用户主键。 */
        private Long id;
        /** 登录账号。 */
        private String username;
        /** 显示名称。 */
        private String displayName;
        /** 头像地址。 */
        private String avatarUrl;
        /** 邮箱地址。 */
        private String email;
        /** 手机号。 */
        private String mobile;
    }
}
