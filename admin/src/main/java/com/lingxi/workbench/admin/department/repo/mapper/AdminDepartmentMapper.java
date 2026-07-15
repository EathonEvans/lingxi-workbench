package com.lingxi.workbench.admin.department.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.department.model.entity.AdminDepartmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门表及部门引用关系查询 Mapper。
 */
@Mapper
public interface AdminDepartmentMapper extends BaseMapper<AdminDepartmentEntity> {

    @Select("SELECT COUNT(1) FROM lingxi_core.admin_account WHERE department_id = #{departmentId}")
    long countAccounts(@Param("departmentId") Long departmentId);

    @Select("SELECT COUNT(1) FROM lingxi_core.admin_role_department WHERE department_id = #{departmentId}")
    long countRoleReferences(@Param("departmentId") Long departmentId);

    /** 判断管理员是否通过任一启用角色拥有全公司数据范围。 */
    @Select("""
            SELECT COUNT(1) > 0
            FROM lingxi_core.admin_account_role account_role
            JOIN lingxi_core.admin_role role ON role.id = account_role.role_id
            WHERE account_role.account_id = #{accountId}
              AND role.status = 'ACTIVE'
              AND role.data_scope = 'ALL'
            """)
    boolean hasAllDataScope(@Param("accountId") Long accountId);

    /**
     * 计算管理员可访问的部门主键。
     * DEPARTMENT 取本人部门，DEPARTMENT_AND_CHILDREN 递归取本人部门及子部门，CUSTOM 取角色显式部门。
     */
    @Select("""
            WITH RECURSIVE department_tree AS (
                SELECT account.department_id AS id
                FROM lingxi_core.admin_account account
                JOIN lingxi_core.admin_department department ON department.id = account.department_id
                JOIN lingxi_core.admin_account_role account_role ON account_role.account_id = account.id
                JOIN lingxi_core.admin_role role ON role.id = account_role.role_id
                WHERE account.id = #{accountId}
                  AND account.department_id IS NOT NULL
                  AND department.status = 'ACTIVE'
                  AND role.status = 'ACTIVE'
                  AND role.data_scope = 'DEPARTMENT_AND_CHILDREN'

                UNION

                SELECT child.id
                FROM lingxi_core.admin_department child
                JOIN department_tree parent ON child.parent_id = parent.id
                WHERE child.status = 'ACTIVE'
            ), scoped_department AS (
                SELECT account.department_id AS id
                FROM lingxi_core.admin_account account
                JOIN lingxi_core.admin_department department ON department.id = account.department_id
                JOIN lingxi_core.admin_account_role account_role ON account_role.account_id = account.id
                JOIN lingxi_core.admin_role role ON role.id = account_role.role_id
                WHERE account.id = #{accountId}
                  AND account.department_id IS NOT NULL
                  AND department.status = 'ACTIVE'
                  AND role.status = 'ACTIVE'
                  AND role.data_scope = 'DEPARTMENT'

                UNION

                SELECT role_department.department_id AS id
                FROM lingxi_core.admin_account_role account_role
                JOIN lingxi_core.admin_role role ON role.id = account_role.role_id
                JOIN lingxi_core.admin_role_department role_department ON role_department.role_id = role.id
                JOIN lingxi_core.admin_department department ON department.id = role_department.department_id
                WHERE account_role.account_id = #{accountId}
                  AND role.status = 'ACTIVE'
                  AND department.status = 'ACTIVE'
                  AND role.data_scope = 'CUSTOM'

                UNION

                SELECT id FROM department_tree
            )
            SELECT DISTINCT id FROM scoped_department WHERE id IS NOT NULL ORDER BY id
            """)
    List<Long> selectAccessibleDepartmentIds(@Param("accountId") Long accountId);
}
