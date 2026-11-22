package com.lingxi.workbench.admin.sys.role.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.sys.role.model.request.AssignIdsRequest;
import com.lingxi.workbench.admin.sys.role.service.SysRoleService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 系统用户与角色关联 API。
 *
 * <p>该接口归属于系统用户资源，因此 URL 放在 sys-users 下；实现仍复用角色服务，
 * 避免 Controller 直接操作关联表。</p>
 */
@RestController
@RequestMapping("/api/v1/sys-users")
@RequiredArgsConstructor
public class SysUserRoleController {

    private final SysRoleService sysRoleService;

    /** 查询指定系统用户当前拥有的角色主键，用于角色分配弹窗回显。 */
    @GetMapping("/list-role-ids/{userId}")
    @SaCheckPermission("system:user:assign-role")
    public ApiResponse<Set<Long>> listRoleIds(@PathVariable Long userId) {
        return ApiResponse.success(sysRoleService.listUserRoleIds(userId));
    }

    /** 全量覆盖指定系统用户拥有的角色。 */
    @PostMapping("/assign-roles/{userId}")
    @SaCheckPermission("system:user:assign-role")
    public ApiResponse<Void> assignRoles(@PathVariable Long userId,
                                         @Valid @RequestBody AssignIdsRequest request) {
        sysRoleService.assignUserRoles(userId, request.ids());
        return ApiResponse.success(null);
    }
}
