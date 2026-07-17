package com.lingxi.workbench.admin.sys.department.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.sys.department.model.entity.SysDepartmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门表及部门引用关系查询 Mapper。
 */
@Mapper
public interface SysDepartmentMapper extends BaseMapper<SysDepartmentEntity> {

    @Select("SELECT COUNT(1) FROM lingxi_core.sys_user WHERE department_id = #{departmentId}")
    long countUsers(@Param("departmentId") Long departmentId);

    @Select("SELECT COUNT(1) FROM lingxi_core.sys_role_department WHERE department_id = #{departmentId}")
    long countRoleReferences(@Param("departmentId") Long departmentId);

    /** 判断管理员是否通过任一启用角色拥有全公司数据范围。 */
    @Select("""
            SELECT COUNT(1) > 0
            FROM lingxi_core.sys_user_role user_role
            JOIN lingxi_core.sys_role role ON role.id = user_role.role_id
            WHERE user_role.user_id = #{userId}
              AND role.status = 'ACTIVE'
              AND role.data_scope = 'ALL'
            """)
    boolean hasAllDataScope(@Param("userId") Long userId);

    /**
     * 计算管理员可访问的部门主键。
     * DEPARTMENT 取本人部门，DEPARTMENT_AND_CHILDREN 递归取本人部门及子部门，CUSTOM 取角色显式部门。
     */
    @Select("""
            WITH RECURSIVE department_tree AS (
                SELECT user.department_id AS id
                FROM lingxi_core.sys_user user
                JOIN lingxi_core.sys_department department ON department.id = user.department_id
                JOIN lingxi_core.sys_user_role user_role ON user_role.user_id = user.id
                JOIN lingxi_core.sys_role role ON role.id = user_role.role_id
                WHERE user.id = #{userId}
                  AND user.department_id IS NOT NULL
                  AND department.status = 'ACTIVE'
                  AND role.status = 'ACTIVE'
                  AND role.data_scope = 'DEPARTMENT_AND_CHILDREN'

                UNION

                SELECT child.id
                FROM lingxi_core.sys_department child
                JOIN department_tree parent ON child.parent_id = parent.id
                WHERE child.status = 'ACTIVE'
            ), scoped_department AS (
                SELECT user.department_id AS id
                FROM lingxi_core.sys_user user
                JOIN lingxi_core.sys_department department ON department.id = user.department_id
                JOIN lingxi_core.sys_user_role user_role ON user_role.user_id = user.id
                JOIN lingxi_core.sys_role role ON role.id = user_role.role_id
                WHERE user.id = #{userId}
                  AND user.department_id IS NOT NULL
                  AND department.status = 'ACTIVE'
                  AND role.status = 'ACTIVE'
                  AND role.data_scope = 'DEPARTMENT'

                UNION

                SELECT role_department.department_id AS id
                FROM lingxi_core.sys_user_role user_role
                JOIN lingxi_core.sys_role role ON role.id = user_role.role_id
                JOIN lingxi_core.sys_role_department role_department ON role_department.role_id = role.id
                JOIN lingxi_core.sys_department department ON department.id = role_department.department_id
                WHERE user_role.user_id = #{userId}
                  AND role.status = 'ACTIVE'
                  AND department.status = 'ACTIVE'
                  AND role.data_scope = 'CUSTOM'

                UNION

                SELECT id FROM department_tree
            )
            SELECT DISTINCT id FROM scoped_department WHERE id IS NOT NULL ORDER BY id
            """)
    List<Long> selectAccessibleDepartmentIds(@Param("userId") Long userId);
}
