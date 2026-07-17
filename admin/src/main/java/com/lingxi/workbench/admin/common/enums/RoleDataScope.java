package com.lingxi.workbench.admin.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 角色可访问的数据范围。
 */
@Getter
@RequiredArgsConstructor
public enum RoleDataScope {
    /**
     * 全部数据。
     */
    ALL("ALL", "全部数据"),
    /**
     * 当前部门及其所有下级部门。
     */
    DEPARTMENT_AND_CHILDREN("DEPARTMENT_AND_CHILDREN", "当前部门及下级部门"),
    /**
     * 仅当前部门。
     */
    DEPARTMENT("DEPARTMENT", "当前部门"),
    /**
     * 仅当前用户创建或负责的数据。
     */
    SELF("SELF", "当前用户"),
    /**
     * 由角色显式关联指定部门。
     */
    CUSTOM("CUSTOM", "指定部门");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
