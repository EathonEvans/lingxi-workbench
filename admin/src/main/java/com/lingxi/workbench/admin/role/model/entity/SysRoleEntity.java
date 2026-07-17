package com.lingxi.workbench.admin.role.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.role.common.enums.RoleDataScope;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端角色数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_role")
public class SysRoleEntity extends AuditTimeEntity {

    private Long id;
    private String name;
    private String code;
    private RoleDataScope dataScope;
    private Integer sortOrder;
    private Boolean builtIn;
    private EnableStatus status;
    private String remark;
}
