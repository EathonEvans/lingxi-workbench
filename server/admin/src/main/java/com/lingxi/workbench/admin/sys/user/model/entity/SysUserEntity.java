package com.lingxi.workbench.admin.sys.user.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.SysUserStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统用户数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_user")
public class SysUserEntity extends TimeEntity {

    /** 用户主键。 */
    private Long id;

    /** 登录账号，全局唯一，存储前统一转为小写并去除前后空格。 */
    private String username;

    /** BCrypt 密码哈希，禁止存储明文密码。 */
    private String passwordHash;

    /** 显示名称。 */
    private String displayName;

    /** 头像地址。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;

    /** 邮箱地址，唯一。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    /** 手机号，唯一。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mobile;

    /** 管理员所属部门，用于后续部门级数据权限计算。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long departmentId;

    /** 账号状态：ACTIVE / DISABLED。 */
    private SysUserStatus status;

    /** 仅用于账号列表联表查询，不属于账号主表字段。 */
    @TableField(exist = false)
    private Boolean applicationAccessEnabled;

    /** 是否已分配至少一个有效管理角色。 */
    @TableField(exist = false)
    private Boolean adminAccessEnabled;
}
