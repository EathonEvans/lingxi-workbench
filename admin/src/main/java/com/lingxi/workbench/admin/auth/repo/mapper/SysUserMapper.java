package com.lingxi.workbench.admin.auth.repo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lingxi.workbench.admin.auth.model.entity.SysUserEntity;
import com.lingxi.workbench.admin.auth.model.request.SysUserPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

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

    /** 按条件分页查询统一账号及其两端访问状态。 */
    @Select("""
            <script>
            """ + USER_WITH_ACCESS_SQL + """
            WHERE 1 = 1
            <if test='query.username != null'>
                AND sys_user.username ILIKE CONCAT('%', #{query.username}, '%')
            </if>
            <if test='query.displayName != null'>
                AND sys_user.display_name ILIKE CONCAT('%', #{query.displayName}, '%')
            </if>
            <if test='query.status != null'>
                AND sys_user.status = #{query.status}
            </if>
            ORDER BY sys_user.id
            </script>
            """)
    IPage<SysUserEntity> selectUserPage(IPage<SysUserEntity> page,
                                        @Param("query") SysUserPageQuery query);

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
