package com.lingxi.workbench.admin.role.model.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * 角色、菜单或部门关联关系的批量分配请求。
 */
public record AssignIdsRequest(
        @NotNull(message = "分配ID集合不能为空")
        Set<Long> ids
) {
}
