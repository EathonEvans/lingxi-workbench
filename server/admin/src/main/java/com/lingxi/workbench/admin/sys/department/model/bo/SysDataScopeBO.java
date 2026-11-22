package com.lingxi.workbench.admin.sys.department.model.bo;

import java.util.Set;

/**
 * 管理员数据权限的统一计算结果。
 *
 * @param allData       是否可以访问全公司数据
 * @param selfOnly      是否只能访问本人创建或负责的数据
 * @param departmentIds 可访问的部门主键；allData 或 selfOnly 为 true 时通常为空
 */
public record SysDataScopeBO(
        boolean allData,
        boolean selfOnly,
        Set<Long> departmentIds
) {

    public SysDataScopeBO {
        departmentIds = Set.copyOf(departmentIds);
        if (allData && selfOnly) {
            throw new IllegalArgumentException("全量数据范围与仅本人范围不能同时生效");
        }
    }
}
