package com.lingxi.workbench.admin.menu.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.menu.common.enums.MenuType;

import java.util.List;

/**
 * 管理端菜单树节点。
 */
public record AdminMenuVO(
        Long id,
        Long parentId,
        String name,
        String code,
        MenuType menuType,
        String routePath,
        String component,
        String icon,
        String permissionCode,
        Integer sortOrder,
        Boolean visible,
        EnableStatus status,
        List<AdminMenuVO> children
) {
}
