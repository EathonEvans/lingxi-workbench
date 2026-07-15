package com.lingxi.workbench.admin.role.repo.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 用户角色、角色菜单和角色部门关联关系 Mapper。
 */
@Mapper
public interface SysRoleRelationMapper {

    @Select("SELECT role_id FROM lingxi_core.sys_user_role WHERE user_id = #{userId} ORDER BY role_id")
    List<Long> selectUserRoleIds(@Param("userId") Long userId);

    @Select("SELECT menu_id FROM lingxi_core.sys_role_menu WHERE role_id = #{roleId} ORDER BY menu_id")
    List<Long> selectRoleMenuIds(@Param("roleId") Long roleId);

    @Select("""
            SELECT department_id
            FROM lingxi_core.sys_role_department
            WHERE role_id = #{roleId}
            ORDER BY department_id
            """)
    List<Long> selectRoleDepartmentIds(@Param("roleId") Long roleId);

    @Select("SELECT COUNT(1) FROM lingxi_core.sys_user WHERE id = #{userId}")
    long countUser(@Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM lingxi_core.sys_user_role WHERE role_id = #{roleId}")
    long countRoleUsers(@Param("roleId") Long roleId);

    /** 查询除指定账号外仍拥有目标角色编码的管理员数量。 */
    @Select("""
            SELECT COUNT(1)
            FROM lingxi_core.sys_user_role user_role
            JOIN lingxi_core.sys_role role ON role.id = user_role.role_id
            JOIN lingxi_core.sys_user user ON user.id = user_role.user_id
            WHERE role.code = #{roleCode}
              AND user_role.user_id <> #{excludeUserId}
              AND role.status = 'ACTIVE'
              AND user.status = 'ACTIVE'
            """)
    long countOtherUsersByRoleCode(@Param("excludeUserId") Long excludeUserId,
                                      @Param("roleCode") String roleCode);

    @Delete("DELETE FROM lingxi_core.sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("""
            <script>
            INSERT INTO lingxi_core.sys_user_role (user_id, role_id) VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId})
            </foreach>
            </script>
            """)
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") Set<Long> roleIds);

    @Delete("DELETE FROM lingxi_core.sys_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") Long roleId);

    @Insert("""
            <script>
            INSERT INTO lingxi_core.sys_role_menu (role_id, menu_id) VALUES
            <foreach collection="menuIds" item="menuId" separator=",">
                (#{roleId}, #{menuId})
            </foreach>
            </script>
            """)
    int insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") Set<Long> menuIds);

    @Delete("DELETE FROM lingxi_core.sys_role_department WHERE role_id = #{roleId}")
    int deleteRoleDepartments(@Param("roleId") Long roleId);

    @Insert("""
            <script>
            INSERT INTO lingxi_core.sys_role_department (role_id, department_id) VALUES
            <foreach collection="departmentIds" item="departmentId" separator=",">
                (#{roleId}, #{departmentId})
            </foreach>
            </script>
            """)
    int insertRoleDepartments(@Param("roleId") Long roleId, @Param("departmentIds") Set<Long> departmentIds);
}
