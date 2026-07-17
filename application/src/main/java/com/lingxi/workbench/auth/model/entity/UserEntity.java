package com.lingxi.workbench.auth.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.auth.common.enums.UserStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

/**
 * 应用端用户数据库实体，映射统一账号主表。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_user")
public class UserEntity {

    /** 用户主键。 */
    private Long id;

    /** 登录账号。 */
    private String username;

    /** BCrypt 密码哈希。 */
    private String passwordHash;

    /** 显示名称。 */
    private String displayName;

    /** 头像地址。 */
    private String avatarUrl;

    /** 邮箱地址。 */
    private String email;

    /** 手机号。 */
    private String mobile;

    /** 账号状态：ACTIVE / DISABLED。 */
    private UserStatus status;

    /** 是否已开启应用端访问权限，联表查询字段。 */
    @TableField(exist = false)
    private Boolean applicationAccessEnabled;
}
