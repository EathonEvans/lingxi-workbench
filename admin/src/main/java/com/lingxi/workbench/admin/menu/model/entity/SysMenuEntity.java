package com.lingxi.workbench.admin.menu.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.menu.common.enums.MenuType;
import com.lingxi.workbench.framework.persistence.model.AuditTimeEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端菜单数据库实体。
 */
@Getter
@Setter
@TableName("lingxi_core.sys_menu")
public class SysMenuEntity extends AuditTimeEntity {

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
}
