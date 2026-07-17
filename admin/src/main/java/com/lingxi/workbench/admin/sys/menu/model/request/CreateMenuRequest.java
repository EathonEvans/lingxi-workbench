package com.lingxi.workbench.admin.sys.menu.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.MenuType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增菜单请求。
 */
public record CreateMenuRequest(
        /** 上级菜单主键，根目录为空。 */
        Long parentId,

        /** 菜单名称。 */
        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 64, message = "菜单名称长度不能超过64个字符")
        String name,

        /** 菜单编码，仅支持大写字母、数字和下划线。 */
        @NotBlank(message = "菜单编码不能为空")
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "菜单编码必须使用大写字母、数字和下划线")
        @Size(max = 64, message = "菜单编码长度不能超过64个字符")
        String code,

        /** 菜单类型：DIRECTORY / MENU / BUTTON。 */
        @NotNull(message = "菜单类型不能为空")
        MenuType menuType,

        /** 前端路由地址。 */
        @Size(max = 255, message = "路由长度不能超过255个字符")
        String routePath,

        /** 前端组件标识。 */
        @Size(max = 255, message = "组件标识长度不能超过255个字符")
        String component,

        /** 图标标识。 */
        @Size(max = 128, message = "图标标识长度不能超过128个字符")
        String icon,

        /** 权限标识。 */
        @Size(max = 128, message = "权限编码长度不能超过128个字符")
        String permissionCode,

        /** 同级排序序号。 */
        Integer sortOrder,

        /** 是否在前端导航中显示。 */
        @NotNull(message = "是否显示不能为空")
        Boolean visible,

        /** 菜单状态。 */
        @NotNull(message = "菜单状态不能为空")
        EnableStatus status
) {
}
