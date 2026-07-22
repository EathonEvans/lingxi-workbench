package com.lingxi.workbench.auth.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户表 MyBatis Mapper，封装基础 CRUD 与联表查询。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 根据用户名查询用户，联表获取应用端访问权限标记。
     *
     * @param username 用户名
     * @return 用户实体，不存在时返回 null
     */
    @Select("""
            SELECT sys_user.*,
                   COALESCE(application_access.enabled, FALSE) AS application_access_enabled
            FROM lingxi_core.sys_user sys_user
            LEFT JOIN lingxi_core.sys_user_application_access application_access
                   ON application_access.user_id = sys_user.id
            WHERE sys_user.username = #{username}
            """)
    UserEntity selectByUsernameWithAccess(@Param("username") String username);

    /**
     * 根据用户 ID 查询用户，联表获取应用端访问权限标记。
     *
     * @param id 用户主键
     * @return 用户实体，不存在时返回 null
     */
    @Select("""
            SELECT sys_user.*,
                   COALESCE(application_access.enabled, FALSE) AS application_access_enabled
            FROM lingxi_core.sys_user sys_user
            LEFT JOIN lingxi_core.sys_user_application_access application_access
                   ON application_access.user_id = sys_user.id
            WHERE sys_user.id = #{id}
            """)
    UserEntity selectByIdWithAccess(@Param("id") Long id);
}
