package com.lingxi.workbench.admin.department.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;

import java.util.List;

/**
 * 部门树节点响应模型。
 */
public record AdminDepartmentVO(
        Long id,
        Long parentId,
        String name,
        String code,
        Integer sortOrder,
        EnableStatus status,
        List<AdminDepartmentVO> children
) {
}
