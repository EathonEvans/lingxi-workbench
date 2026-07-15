package com.lingxi.workbench.admin.role.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.role.model.entity.SysRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色表及角色授权关系查询 Mapper。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRoleEntity> {

    /**
     * 按编码查询并锁定角色行，用于串行化“最后一个 ROOT 管理员”校验。
     */
    @Select("SELECT * FROM lingxi_core.sys_role WHERE code = #{code} FOR UPDATE")
    SysRoleEntity selectByCodeForUpdate(@Param("code") String code);

    /**
     * 查询管理员当前生效的角色编码。
     */
    @Select("""
            SELECT role.code
            FROM lingxi_core.sys_role role
            JOIN lingxi_core.sys_user_role user_role ON user_role.role_id = role.id
            WHERE user_role.user_id = #{userId}
              AND role.status = 'ACTIVE'
            ORDER BY role.sort_order, role.id
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
