package com.lingxi.workbench.admin.auth.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.auth.model.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {

    String USER_WITH_ACCESS_SQL = """
            SELECT sys_user.*,
                   COALESCE(application_access.enabled, FALSE) AS application_access_enabled,
                   EXISTS (
                       SELECT 1
                       FROM lingxi_core.sys_user_role user_role
                       JOIN lingxi_core.sys_role role ON role.id = user_role.role_id
                       WHERE user_role.user_id = sys_user.id
                         AND role.status = 'ACTIVE'
                   ) AS admin_access_enabled
            FROM lingxi_core.sys_user sys_user
            LEFT JOIN lingxi_core.sys_user_application_access application_access
                   ON application_access.user_id = sys_user.id
            """;

    /** 查询统一账号及其两端访问状态。 */
    @Select(USER_WITH_ACCESS_SQL + " ORDER BY sys_user.id")
    List<SysUserEntity> selectUserList();

    /** 按用户主键查询账号及其两端访问状态。 */
    @Select(USER_WITH_ACCESS_SQL + " WHERE sys_user.id = #{id}")
    SysUserEntity selectUserByIdWithAccess(@Param("id") Long id);

    /** 按登录名查询账号及其两端访问状态。 */
    @Select(USER_WITH_ACCESS_SQL + " WHERE sys_user.username = #{username}")
    SysUserEntity selectUserByUsernameWithAccess(@Param("username") String username);

    @Insert("""
            INSERT INTO lingxi_core.sys_user_application_access
                (user_id, enabled, enabled_at, disabled_at, updated_by, updated_at)
            VALUES
                (#{userId}, #{enabled},
                 CASE WHEN #{enabled} THEN CURRENT_TIMESTAMP ELSE NULL END,
                 CASE WHEN #{enabled} THEN NULL ELSE CURRENT_TIMESTAMP END,
                 #{operatorId}, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id) DO UPDATE
            SET enabled = EXCLUDED.enabled,
                enabled_at = CASE WHEN EXCLUDED.enabled THEN CURRENT_TIMESTAMP ELSE sys_user_application_access.enabled_at END,
                disabled_at = CASE WHEN EXCLUDED.enabled THEN NULL ELSE CURRENT_TIMESTAMP END,
                updated_by = EXCLUDED.updated_by,
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertApplicationAccess(@Param("userId") Long userId,
                                @Param("enabled") boolean enabled,
                                @Param("operatorId") Long operatorId);

    @Insert("""
            INSERT INTO lingxi_audit.sys_user_change_log
                (target_user_id, operator_user_id, action, change_summary)
            VALUES (#{targetUserId}, #{operatorUserId}, #{action}, #{summary})
            """)
    int insertChangeLog(@Param("targetUserId") Long targetUserId,
                        @Param("operatorUserId") Long operatorUserId,
                        @Param("action") String action,
                        @Param("summary") String summary);
}
