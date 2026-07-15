package com.lingxi.workbench.admin.config;

import cn.dev33.satoken.stp.StpInterface;
import com.lingxi.workbench.admin.auth.service.AdminPermissionService;
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
public class AdminStpInterface implements StpInterface {

    private final AdminPermissionService adminPermissionService;
    private final SaTokenExtendConfig saTokenExtendConfig;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!saTokenExtendConfig.getLoginType().equals(loginType)) {
            return List.of();
        }
        return adminPermissionService.listPermissions(toAccountId(loginId));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!saTokenExtendConfig.getLoginType().equals(loginType)) {
            return List.of();
        }
        return adminPermissionService.listRoles(toAccountId(loginId));
    }

    /** Sa-Token 可能以数字或字符串保存登录主键，这里统一转换为 Long。 */
    private static Long toAccountId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(loginId));
    }
}
