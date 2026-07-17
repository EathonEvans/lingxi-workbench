package com.lingxi.workbench.admin.sys.department.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;

import java.util.List;

/**
 * 部门树节点响应模型。
 */
public record SysDepartmentVO(
        /** 部门主键。 */
        Long id,
        /** 上级部门主键。 */
        Long parentId,
        /** 部门名称。 */
        String name,
        /** 部门编码。 */
        String code,
        /** 同级排序序号。 */
        Integer sortOrder,
        /** 启用状态。 */
        EnableStatus status,
        /** 子部门列表。 */
        List<SysDepartmentVO> children
) {
}
