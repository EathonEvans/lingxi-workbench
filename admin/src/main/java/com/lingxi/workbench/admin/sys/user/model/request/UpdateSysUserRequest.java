package com.lingxi.workbench.admin.sys.user.model.request;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/** 修改系统用户基础资料请求，不包含密码和角色。 */
public record UpdateSysUserRequest(
        /** 显示名称。 */
        @NotBlank(message = "显示名称不能为空")
        @Size(max = 64, message = "显示名称长度不能超过64个字符")
        String displayName,

        /** 头像地址。 */
        @Size(max = 512, message = "头像地址长度不能超过512个字符")
        String avatarUrl,

        /** 邮箱地址。 */
        @Size(max = 254, message = "邮箱地址长度不能超过254个字符")
        @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$", message = "邮箱地址格式不正确")
        String email,

        /** 手机号。 */
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String mobile,

        /** 所属部门主键。 */
        Long departmentId,

        /** 是否开启应用端访问权限。 */
        @NotNull(message = "应用端访问权限不能为空")
        Boolean applicationAccessEnabled,

        /** 账号状态。 */
        @NotNull(message = "账号状态不能为空")
        SysUserStatus status
) {
}
