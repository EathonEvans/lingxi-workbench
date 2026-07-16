package com.lingxi.workbench.auth.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("lingxi_core.sys_user")
public class UserEntity {

    private Long id;

    private String username;

    private String passwordHash;

    private String displayName;

    private String avatarUrl;

    private String email;

    private String mobile;

    private String status;

    @TableField(exist = false)
    private Boolean applicationAccessEnabled;
}
