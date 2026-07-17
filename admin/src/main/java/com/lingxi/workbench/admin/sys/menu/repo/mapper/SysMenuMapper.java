package com.lingxi.workbench.admin.sys.menu.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.sys.menu.model.entity.SysMenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单表和管理员授权菜单查询 Mapper。
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenuEntity> {

    @Select("SELECT COUNT(1) FROM lingxi_core.sys_role_menu WHERE menu_id = #{menuId}")
    long countRoleReferences(@Param("menuId") Long menuId);

    /**
     * 查询管理员拥有的全部生效菜单。
     *
     * <p>普通角色从显式分配节点向上递归补齐父节点，避免只勾选页面或按钮时导航树缺少父目录；
     * ROOT 角色自动拥有所有菜单，避免新增菜单后遗漏授权。</p>
     */
    @Select("""
            WITH RECURSIVE granted_menu AS (
                SELECT menu.*
                FROM lingxi_core.sys_menu menu
                JOIN lingxi_core.sys_role_menu role_menu ON role_menu.menu_id = menu.id
                JOIN lingxi_core.sys_role role ON role.id = role_menu.role_id
                JOIN lingxi_core.sys_user_role user_role ON user_role.role_id = role.id
                WHERE user_role.user_id = #{userId}
                  AND role.status = 'ACTIVE'

                UNION

                SELECT parent.*
                FROM lingxi_core.sys_menu parent
                JOIN granted_menu child ON child.parent_id = parent.id
            )
            SELECT DISTINCT menu.*
            FROM lingxi_core.sys_menu menu
            WHERE menu.status = 'ACTIVE'
              AND (
                  EXISTS (
                      SELECT 1
                      FROM lingxi_core.sys_user_role user_role
                      JOIN lingxi_core.sys_role role ON role.id = user_role.role_id
                      WHERE user_role.user_id = #{userId}
                        AND role.code = 'ROOT'
                        AND role.status = 'ACTIVE'
                  )
                  OR menu.id IN (SELECT id FROM granted_menu)
              )
            ORDER BY menu.sort_order, menu.id
            """)
    List<SysMenuEntity> selectGrantedMenus(@Param("userId") Long userId);
}
