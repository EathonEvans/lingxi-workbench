package com.lingxi.workbench.admin.auth.model.request;

import com.lingxi.workbench.admin.auth.common.enums.AdminAccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 修改后台管理员账号基础资料请求，不包含密码和角色。 */
public record UpdateAdminAccountRequest(
        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称长度不能超过64个字符")
        String displayName,

        @Size(max = 512, message = "头像地址长度不能超过512个字符")
        String avatarUrl,

        Long departmentId,

        @NotNull(message = "账号状态不能为空")
        AdminAccountStatus status
) {
}
