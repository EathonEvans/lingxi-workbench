package com.lingxi.workbench.framework.context;

import java.util.Objects;

/**
 * 当前请求中的登录用户快照。
 */
public record LoginUser(
        Long id,
        String username,
        String displayName,
        String avatarUrl
) {

    public LoginUser {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(username, "username 不能为空");
        Objects.requireNonNull(displayName, "displayName 不能为空");
    }
}
