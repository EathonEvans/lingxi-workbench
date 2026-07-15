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
public interface AdminRoleRelationMapper {

    @Select("SELECT role_id FROM lingxi_core.admin_account_role WHERE account_id = #{accountId} ORDER BY role_id")
    List<Long> selectAccountRoleIds(@Param("accountId") Long accountId);

    @Select("SELECT menu_id FROM lingxi_core.admin_role_menu WHERE role_id = #{roleId} ORDER BY menu_id")
    List<Long> selectRoleMenuIds(@Param("roleId") Long roleId);

    @Select("""
            SELECT department_id
            FROM lingxi_core.admin_role_department
            WHERE role_id = #{roleId}
            ORDER BY department_id
            """)
    List<Long> selectRoleDepartmentIds(@Param("roleId") Long roleId);

    @Select("SELECT COUNT(1) FROM lingxi_core.admin_account WHERE id = #{accountId}")
    long countAccount(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(1) FROM lingxi_core.admin_account_role WHERE role_id = #{roleId}")
    long countRoleAccounts(@Param("roleId") Long roleId);

    /** 查询除指定账号外仍拥有目标角色编码的管理员数量。 */
    @Select("""
            SELECT COUNT(1)
            FROM lingxi_core.admin_account_role account_role
            JOIN lingxi_core.admin_role role ON role.id = account_role.role_id
            JOIN lingxi_core.admin_account account ON account.id = account_role.account_id
            WHERE role.code = #{roleCode}
              AND account_role.account_id <> #{excludeAccountId}
              AND role.status = 'ACTIVE'
              AND account.status = 'ACTIVE'
            """)
    long countOtherAccountsByRoleCode(@Param("excludeAccountId") Long excludeAccountId,
                                      @Param("roleCode") String roleCode);

    @Delete("DELETE FROM lingxi_core.admin_account_role WHERE account_id = #{accountId}")
    int deleteAccountRoles(@Param("accountId") Long accountId);

    @Insert("""
            <script>
            INSERT INTO lingxi_core.admin_account_role (account_id, role_id) VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{accountId}, #{roleId})
            </foreach>
            </script>
            """)
    int insertAccountRoles(@Param("accountId") Long accountId, @Param("roleIds") Set<Long> roleIds);

    @Delete("DELETE FROM lingxi_core.admin_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") Long roleId);

    @Insert("""
            <script>
            INSERT INTO lingxi_core.admin_role_menu (role_id, menu_id) VALUES
            <foreach collection="menuIds" item="menuId" separator=",">
                (#{roleId}, #{menuId})
            </foreach>
            </script>
            """)
    int insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") Set<Long> menuIds);

    @Delete("DELETE FROM lingxi_core.admin_role_department WHERE role_id = #{roleId}")
    int deleteRoleDepartments(@Param("roleId") Long roleId);

    @Insert("""
            <script>
            INSERT INTO lingxi_core.admin_role_department (role_id, department_id) VALUES
            <foreach collection="departmentIds" item="departmentId" separator=",">
                (#{roleId}, #{departmentId})
            </foreach>
            </script>
            """)
    int insertRoleDepartments(@Param("roleId") Long roleId, @Param("departmentIds") Set<Long> departmentIds);
}
