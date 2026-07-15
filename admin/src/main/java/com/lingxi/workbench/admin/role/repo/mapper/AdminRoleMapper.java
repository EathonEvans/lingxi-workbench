package com.lingxi.workbench.admin.role.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.role.model.entity.AdminRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色表及角色授权关系查询 Mapper。
 */
@Mapper
public interface AdminRoleMapper extends BaseMapper<AdminRoleEntity> {

    /**
     * 按编码查询并锁定角色行，用于串行化“最后一个 ROOT 管理员”校验。
     */
    @Select("SELECT * FROM lingxi_core.admin_role WHERE code = #{code} FOR UPDATE")
    AdminRoleEntity selectByCodeForUpdate(@Param("code") String code);

    /**
     * 查询管理员当前生效的角色编码。
     */
    @Select("""
            SELECT role.code
            FROM lingxi_core.admin_role role
            JOIN lingxi_core.admin_account_role account_role ON account_role.role_id = role.id
            WHERE account_role.account_id = #{accountId}
              AND role.status = 'ACTIVE'
            ORDER BY role.sort_order, role.id
            """)
    List<String> selectRoleCodesByAccountId(@Param("accountId") Long accountId);
}
