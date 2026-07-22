package com.lingxi.workbench.framework.context;

import java.util.Objects;

/**
 * 当前请求中的登录用户快照。
 *
 * <p>作为不可变记录，在认证拦截器中构建后放入请求上下文，
 * 供后续 Controller 和 Service 层获取当前登录用户信息。</p>
 * @param id 用户主键
 * @param username 登录账号
 * @param displayName 显示名称
 * @param avatarUrl 头像地址
 */
public record LoginUser(
        Long id,
        String username,
        String displayName,
        String avatarUrl) {

    /**
     * 紧凑构造器，校验必填字段非空。
     */
    public LoginUser {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(username, "username 不能为空");
        Objects.requireNonNull(displayName, "displayName 不能为空");
    }
}
