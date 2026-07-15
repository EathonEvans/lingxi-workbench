package com.lingxi.workbench.admin.auth.model.request;

import com.lingxi.workbench.admin.auth.common.enums.AdminAccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新增后台管理员账号请求。 */
public record CreateAdminAccountRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        String username,

        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 72, message = "初始密码长度必须在8到72个字符之间")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称长度不能超过64个字符")
        String displayName,

        Long departmentId,

        @NotNull(message = "账号状态不能为空")
        AdminAccountStatus status
) {
}
