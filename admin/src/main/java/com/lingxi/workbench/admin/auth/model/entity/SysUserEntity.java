package com.lingxi.workbench.admin.auth.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_user")
public class SysUserEntity extends AuditTimeEntity {

    private Long id;

    private String username;

    private String passwordHash;

    private String displayName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mobile;

    /** 管理员所属部门，用于后续部门级数据权限计算。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long departmentId;

    private String status;

    /** 仅用于账号列表联表查询，不属于账号主表字段。 */
    @TableField(exist = false)
    private Boolean applicationAccessEnabled;

    /** 是否已分配至少一个有效管理角色。 */
    @TableField(exist = false)
    private Boolean adminAccessEnabled;
}
