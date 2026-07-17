package com.lingxi.workbench.admin.sys.department.model.entity;

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

    /**
     * 部门主键。
     */
    private Long id;
    /**
     * 上级部门主键，根部门为空。
     */
    private Long parentId;
    /**
     * 部门名称。
     */
    private String name;
    /**
     * 稳定的部门编码，不随部门名称修改。
     */
    private String code;
    /**
     * 同级排序序号。
     */
    private Integer sortOrder;
    /**
     * 启用状态：ACTIVE / DISABLED。
     */
    private EnableStatus status;
}
