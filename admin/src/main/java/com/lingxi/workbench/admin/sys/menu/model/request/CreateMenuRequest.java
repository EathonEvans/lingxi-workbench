package com.lingxi.workbench.admin.sys.menu.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.MenuType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增菜单请求。
 *
 * @param parentId       上级菜单主键，根目录为空
 * @param name           菜单名称
 * @param code           菜单编码，仅支持大写字母、数字和下划线
 * @param menuType       菜单类型：DIRECTORY / MENU / BUTTON
 * @param routePath      前端路由地址
 * @param component      前端组件标识
 * @param icon           图标标识
 * @param permissionCode 权限标识
 * @param sortOrder      同级排序序号
 * @param visible        是否在前端导航中显示
 * @param status         菜单状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CreateMenuRequest {
    /** 上级菜单主键，根目录为空。 */
    private Long parentId;

    /** 菜单名称。 */
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过64个字符")
    private String name;

    /** 菜单编码，仅支持大写字母、数字和下划线。 */
    @NotBlank(message = "菜单编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "菜单编码必须使用大写字母、数字和下划线")
    @Size(max = 64, message = "菜单编码长度不能超过64个字符")
    private String code;

    /** 菜单类型：DIRECTORY / MENU / BUTTON。 */
    @NotNull(message = "菜单类型不能为空")
    private MenuType menuType;

    /** 前端路由地址。 */
    @Size(max = 255, message = "路由长度不能超过255个字符")
    private String routePath;

    /** 前端组件标识。 */
    @Size(max = 255, message = "组件标识长度不能超过255个字符")
    private String component;

    /** 图标标识。 */
    @Size(max = 128, message = "图标标识长度不能超过128个字符")
    private String icon;

    /** 权限标识。 */
    @Size(max = 128, message = "权限编码长度不能超过128个字符")
    private String permissionCode;

    /** 同级排序序号。 */
    private Integer sortOrder;

    /** 是否在前端导航中显示。 */
    @NotNull(message = "是否显示不能为空")
    private Boolean visible;

    /** 菜单状态。 */
    @NotNull(message = "菜单状态不能为空")
    private EnableStatus status;
}