package com.lingxi.workbench.admin.sys.user.model.vo;

/**
 * 当前用户头像响应。
 *
 * @param avatarUrl 带签名的临时访问地址
 * @param avatarObjectKey 持久化对象Key
 */
public record SysAvatarVO(String avatarUrl, String avatarObjectKey) {
}
