package com.lingxi.workbench.admin.role.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.role.common.enums.RoleDataScope;

/**
 * 角色信息响应模型。
 */
public record SysRoleVO(
        Long id,
        String name,
        String code,
        RoleDataScope dataScope,
        Integer sortOrder,
        Boolean builtIn,
        EnableStatus status,
        String remark
) {
}
