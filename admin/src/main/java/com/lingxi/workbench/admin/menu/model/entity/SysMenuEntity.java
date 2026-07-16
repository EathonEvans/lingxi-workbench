package com.lingxi.workbench.admin.menu.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.menu.common.enums.MenuType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 管理端菜单数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_menu")
public class SysMenuEntity {

    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private MenuType menuType;
    private String routePath;
    private String component;
    private String icon;
    private String permissionCode;
    private Integer sortOrder;
    private Boolean visible;
    private EnableStatus status;
    private OffsetDateTime createdAt;

    /** 更新时统一使用数据库时间，避免应用服务器时钟差异。 */
    @TableField(update = "CURRENT_TIMESTAMP")
    private OffsetDateTime updatedAt;
}
