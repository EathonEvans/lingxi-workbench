package com.lingxi.workbench.admin.auth.model.request;

import com.lingxi.workbench.admin.auth.common.enums.SysUserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/** 新增系统用户请求。 */
public record CreateSysUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64个字符")
        String username,

        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 72, message = "初始密码长度必须在8到72个字符之间")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称长度不能超过64个字符")
        String displayName,

        @Size(max = 254, message = "邮箱地址长度不能超过254个字符")
        @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$", message = "邮箱地址格式不正确")
        String email,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String mobile,

        Long departmentId,

        @NotNull(message = "应用端访问权限不能为空")
        Boolean applicationAccessEnabled,

        @NotNull(message = "账号状态不能为空")
        SysUserStatus status
) {
}
