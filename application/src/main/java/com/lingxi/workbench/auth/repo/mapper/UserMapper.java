package com.lingxi.workbench.auth.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    @Select("""
            SELECT sys_user.*,
                   COALESCE(application_access.enabled, FALSE) AS application_access_enabled
            FROM lingxi_core.sys_user sys_user
            LEFT JOIN lingxi_core.sys_user_application_access application_access
                   ON application_access.user_id = sys_user.id
            WHERE sys_user.username = #{username}
            """)
    UserEntity selectByUsernameWithAccess(@Param("username") String username);

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
