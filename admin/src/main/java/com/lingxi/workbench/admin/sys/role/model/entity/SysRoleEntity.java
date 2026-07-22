package com.lingxi.workbench.admin.sys.role.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.RoleDataScope;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端角色数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_role")
public class SysRoleEntity extends TimeEntity {

    /** 角色主键。 */
    private Long id;
    /** 角色名称。 */
    private String name;
    /** 角色编码，全局唯一。 */
    private String code;
    /** 数据范围：ALL / DEPARTMENT_AND_CHILDREN / DEPARTMENT / SELF / CUSTOM。 */
    private RoleDataScope dataScope;
    /** 同级排序序号。 */
    private Integer sortOrder;
    /** 内置角色不允许删除。 */
    private Boolean builtIn;
    /** 启用状态：ACTIVE / DISABLED。 */
    private EnableStatus status;
    /** 备注说明。 */
    private String remark;
}
