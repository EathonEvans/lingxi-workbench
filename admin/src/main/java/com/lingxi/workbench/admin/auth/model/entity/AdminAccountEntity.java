package com.lingxi.workbench.admin.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 管理员账号数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.admin_account")
public class AdminAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String displayName;

    private String avatarUrl;

    /** 管理员所属部门，用于后续部门级数据权限计算。 */
    private Long departmentId;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
