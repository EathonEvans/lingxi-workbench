package com.lingxi.workbench.admin.sys.user.model.request;

import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增系统用户请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CreateSysUserRequest {
    /**
     * 登录账号。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64个字符")
    private String username;

    /**
     * 初始密码。
     */
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 72, message = "初始密码长度必须在8到72个字符之间")
    private String password;

    /**
     * 显示名称。
     */
    @NotBlank(message = "显示名称不能为空")
    @Size(max = 64, message = "显示名称长度不能超过64个字符")
    private String displayName;

    /**
     * 头像对象Key。
     */
    @Size(max = 512, message = "头像对象Key长度不能超过512个字符")
    private String avatarUrl;

    /**
     * 邮箱地址。
     */
    @Size(max = 254, message = "邮箱地址长度不能超过254个字符")
    @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$", message = "邮箱地址格式不正确")
    private String email;

    /**
     * 手机号。
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    /**
     * 所属部门主键。
     */
    private Long departmentId;

    /**
     * 是否开启应用端访问权限。
     */
    @NotNull(message = "应用端访问权限不能为空")
    private Boolean applicationAccessEnabled;

    /**
     * 账号状态。
     */
    @NotNull(message = "账号状态不能为空")
    private SysUserStatus status;
}