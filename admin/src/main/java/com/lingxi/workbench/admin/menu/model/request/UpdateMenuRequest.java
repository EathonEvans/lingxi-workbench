package com.lingxi.workbench.admin.menu.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.menu.common.enums.MenuType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改菜单请求。菜单编码创建后保持稳定。
 */
public record UpdateMenuRequest(
        Long parentId,

        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 64, message = "菜单名称长度不能超过64个字符")
        String name,

        @NotNull(message = "菜单类型不能为空")
        MenuType menuType,

        @Size(max = 255, message = "路由长度不能超过255个字符")
        String routePath,

        @Size(max = 255, message = "组件标识长度不能超过255个字符")
        String component,

        @Size(max = 128, message = "图标标识长度不能超过128个字符")
        String icon,

        @Size(max = 128, message = "权限编码长度不能超过128个字符")
        String permissionCode,

        Integer sortOrder,

        @NotNull(message = "是否显示不能为空")
        Boolean visible,

        @NotNull(message = "菜单状态不能为空")
        EnableStatus status
) {
}
