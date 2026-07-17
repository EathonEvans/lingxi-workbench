package com.lingxi.workbench.admin.department.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端部门数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_department")
public class SysDepartmentEntity extends AuditTimeEntity {

    private Long id;
    /**
     *上级部门id
     */
    private Long parentId;
    /**
     * 部门名称
     */
    private String name;
    /**
     * 部门编码
     */
    private String code;
    /**
     * 排序
     */
    private Integer sortOrder;
    /**
     * 启用状态
     */
    private EnableStatus status;
}
