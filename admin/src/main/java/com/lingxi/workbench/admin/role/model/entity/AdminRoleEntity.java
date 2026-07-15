package com.lingxi.workbench.admin.role.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.role.common.enums.RoleDataScope;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 管理端角色数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.admin_role")
public class AdminRoleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private RoleDataScope dataScope;
    private Integer sortOrder;
    private Boolean builtIn;
    private EnableStatus status;
    private String remark;
    private OffsetDateTime createdAt;

    /** 更新时统一使用数据库时间，避免应用服务器时钟差异。 */
    @TableField(update = "CURRENT_TIMESTAMP")
    private OffsetDateTime updatedAt;
}
