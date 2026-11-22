package com.lingxi.workbench.admin.sys.menu.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.common.enums.MenuType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理端菜单树节点。
 *
 * @param id             菜单主键
 * @param parentId       上级菜单主键
 * @param name           菜单名称
 * @param code           菜单编码
 * @param menuType       菜单类型：DIRECTORY / MENU / BUTTON
 * @param routePath      前端路由地址
 * @param component      前端组件标识
 * @param icon           图标标识
 * @param permissionCode 权限标识
 * @param sortOrder      同级排序序号
 * @param visible        是否在前端导航中显示
 * @param status         启用状态
 * @param children       子菜单列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysMenuVO {
    /** 菜单主键。 */
    private Long id;
    /** 上级菜单主键。 */
    private Long parentId;
    /** 菜单名称。 */
    private String name;
    /** 菜单编码。 */
    private String code;
    /** 菜单类型：DIRECTORY / MENU / BUTTON。 */
    private MenuType menuType;
    /** 前端路由地址。 */
    private String routePath;
    /** 前端组件标识。 */
    private String component;
    /** 图标标识。 */
    private String icon;
    /** 权限标识。 */
    private String permissionCode;
    /** 同级排序序号。 */
    private Integer sortOrder;
    /** 是否在前端导航中显示。 */
    private Boolean visible;
    /** 启用状态。 */
    private EnableStatus status;
    /** 子菜单列表。 */
    private List<SysMenuVO> children;
}