package com.lingxi.workbench.admin.role.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.role.common.enums.RoleDataScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改角色请求。角色编码创建后保持稳定，不允许通过修改接口变更。
 */
public record UpdateRoleRequest(
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        String name,

        @NotNull(message = "数据范围不能为空")
        RoleDataScope dataScope,

        Integer sortOrder,

        @NotNull(message = "角色状态不能为空")
        EnableStatus status,

        @Size(max = 255, message = "备注长度不能超过255个字符")
        String remark
) {
}
