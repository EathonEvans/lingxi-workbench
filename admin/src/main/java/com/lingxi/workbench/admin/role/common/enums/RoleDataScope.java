package com.lingxi.workbench.admin.role.common.enums;

/**
 * 角色可访问的数据范围。
 */
public enum RoleDataScope {
    /** 全部数据。 */
    ALL,
    /** 当前部门及其所有下级部门。 */
    DEPARTMENT_AND_CHILDREN,
    /** 仅当前部门。 */
    DEPARTMENT,
    /** 仅当前用户创建或负责的数据。 */
    SELF,
    /** 由角色显式关联指定部门。 */
    CUSTOM
}
