package com.lingxi.workbench.admin.auth.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端账号密码登录请求参数。
 *
 * @param username 登录账号
 * @param password 登录密码
 */
public record SysLoginRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        String username,


        @NotBlank(message = "密码不能为空")
        @Size(max = 128, message = "密码长度不能超过128个字符")
        String password
) {
}
