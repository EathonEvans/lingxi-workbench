package com.lingxi.workbench.admin.sys.role.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.RoleDataScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增角色请求。
 */
public record CreateRoleRequest(
        /** 角色名称。 */
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        String name,

        /** 角色编码，仅支持大写字母、数字和下划线。 */
        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "角色编码必须使用大写字母、数字和下划线")
        @Size(max = 64, message = "角色编码长度不能超过64个字符")
        String code,

        /** 数据范围。 */
        @NotNull(message = "数据范围不能为空")
        RoleDataScope dataScope,

        /** 同级排序序号。 */
        Integer sortOrder,

        /** 角色状态。 */
        @NotNull(message = "角色状态不能为空")
        EnableStatus status,

        /** 备注说明。 */
        @Size(max = 255, message = "备注长度不能超过255个字符")
        String remark
) {
}
