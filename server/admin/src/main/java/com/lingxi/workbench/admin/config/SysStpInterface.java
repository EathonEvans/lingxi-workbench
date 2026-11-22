package com.lingxi.workbench.admin.config;

import cn.dev33.satoken.stp.StpInterface;
import com.lingxi.workbench.admin.auth.service.SysPermissionService;
import com.lingxi.workbench.framework.auth.config.SaTokenExtendConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限查询接口实现。
 *
 * <p>{@code @SaCheckPermission} 和 {@code @SaCheckRole} 最终会回调本类。
 * 管理端的权限来源于数据库角色、菜单关系，不在代码中写死。</p>
 */
@Component
@RequiredArgsConstructor
public class SysStpInterface implements StpInterface {

    private final SysPermissionService sysPermissionService;
    private final SaTokenExtendConfig saTokenExtendConfig;

    /** Sa-Token 回调：查询当前登录用户的功能权限编码集合。 */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!saTokenExtendConfig.getLoginType().equals(loginType)) {
            return List.of();
        }
        return sysPermissionService.listPermissions(toUserId(loginId));
    }

    /** Sa-Token 回调：查询当前登录用户的角色编码集合。 */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!saTokenExtendConfig.getLoginType().equals(loginType)) {
            return List.of();
        }
        return sysPermissionService.listRoles(toUserId(loginId));
    }

    /** Sa-Token 可能以数字或字符串保存登录主键，这里统一转换为 Long。 */
    private static Long toUserId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(loginId));
    }
}
