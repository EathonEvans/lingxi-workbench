package com.lingxi.workbench.admin.sys.role.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.RoleDataScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改角色请求。角色编码创建后保持稳定，不允许通过修改接口变更。
 */
public record UpdateRoleRequest(
        /** 角色名称。 */
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过64个字符")
        String name,

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
