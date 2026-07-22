package com.lingxi.workbench.admin.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 当前管理员修改头像请求。
 *
 * @param avatarObjectKey 文件上传接口返回的头像对象Key
 */
public record UpdateCurrentAvatarRequest(
        @NotBlank(message = "头像对象Key不能为空")
        @Size(max = 256, message = "头像对象Key长度不能超过256个字符")
        String avatarObjectKey) {
}
