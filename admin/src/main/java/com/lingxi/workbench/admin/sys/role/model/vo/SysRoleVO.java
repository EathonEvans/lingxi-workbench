package com.lingxi.workbench.admin.sys.role.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.RoleDataScope;

/**
 * 角色信息响应模型。
 */
public record SysRoleVO(
        /** 角色主键。 */
        Long id,
        /** 角色名称。 */
        String name,
        /** 角色编码。 */
        String code,
        /** 数据范围。 */
        RoleDataScope dataScope,
        /** 同级排序序号。 */
        Integer sortOrder,
        /** 是否内置角色。 */
        Boolean builtIn,
        /** 启用状态。 */
        EnableStatus status,
        /** 备注说明。 */
        String remark
) {
}
