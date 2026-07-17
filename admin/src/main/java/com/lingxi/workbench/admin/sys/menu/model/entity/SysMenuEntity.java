package com.lingxi.workbench.admin.sys.menu.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.MenuType;
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

    /** 菜单主键。 */
    private Long id;
    /** 上级菜单主键，根目录为空。 */
    private Long parentId;
    /** 菜单名称。 */
    private String name;
    /** 菜单编码，全局唯一。 */
    private String code;
    /** 菜单类型：DIRECTORY / MENU / BUTTON。 */
    private MenuType menuType;
    /** 前端路由地址。 */
    private String routePath;
    /** 前端组件标识。 */
    private String component;
    /** 图标标识。 */
    private String icon;
    /** 后端接口和前端按钮共用的权限标识。 */
    private String permissionCode;
    /** 同级排序序号。 */
    private Integer sortOrder;
    /** 是否在前端导航中显示。 */
    private Boolean visible;
    /** 启用状态：ACTIVE / DISABLED。 */
    private EnableStatus status;
}
