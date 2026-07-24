package com.lingxi.workbench.admin.auth.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理员登录响应，包含 Sa-Token 令牌和管理员基础资料。
 *
 * @param tokenName        Sa-Token 令牌名称
 * @param tokenValue       Sa-Token 令牌值
 * @param expiresInSeconds 令牌过期时间（秒）
 * @param user             管理员基础资料
 */
public record SysLoginVO(
        String tokenName,

        String tokenValue,

        long expiresInSeconds,

        SysProfileVO user
) {

    /**
     * 管理员基础资料，不包含密码哈希等敏感字段。
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class SysProfileVO {
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
         * 所属部门主键。
         */
        private Long departmentId;
        /**
         * 邮箱地址。
         */
        private String email;
        /**
         * 手机号。
         */
        private String mobile;

        public static SysProfileVO of(Long id, String username, String displayName,
                                      String avatarUrl, Long departmentId) {
            return SysProfileVO.builder()
                    .id(id)
                    .username(username)
                    .displayName(displayName)
                    .avatarUrl(avatarUrl)
                    .departmentId(departmentId)
                    .build();
        }
    }
}