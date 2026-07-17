package com.lingxi.workbench.admin.sys.menu.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.MenuType;

import java.util.List;

/**
 * 管理端菜单树节点。
 */
public record SysMenuVO(
        /** 菜单主键。 */
        Long id,
        /** 上级菜单主键。 */
        Long parentId,
        /** 菜单名称。 */
        String name,
        /** 菜单编码。 */
        String code,
        /** 菜单类型：DIRECTORY / MENU / BUTTON。 */
        MenuType menuType,
        /** 前端路由地址。 */
        String routePath,
        /** 前端组件标识。 */
        String component,
        /** 图标标识。 */
        String icon,
        /** 权限标识。 */
        String permissionCode,
        /** 同级排序序号。 */
        Integer sortOrder,
        /** 是否在前端导航中显示。 */
        Boolean visible,
        /** 启用状态。 */
        EnableStatus status,
        /** 子菜单列表。 */
        List<SysMenuVO> children
) {
}
