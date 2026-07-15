package com.lingxi.workbench.admin.role.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.role.model.request.AssignIdsRequest;
import com.lingxi.workbench.admin.role.service.AdminRoleService;
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
 * 管理员与角色关联 API。
 *
 * <p>该接口归属于管理员资源，因此 URL 放在 admin-users 下；实现仍复用角色服务，
 * 避免 Controller 直接操作关联表。</p>
 */
@RestController
@RequestMapping("/api/v1/admin-users")
@RequiredArgsConstructor
public class AdminAccountRoleController {

    private final AdminRoleService adminRoleService;

    /** 查询指定管理员当前拥有的角色主键，用于角色分配弹窗回显。 */
    @GetMapping("/list-role-ids/{accountId}")
    @SaCheckPermission("system:user:assign-role")
    public ApiResponse<Set<Long>> listRoleIds(@PathVariable Long accountId) {
        return ApiResponse.success(adminRoleService.listAccountRoleIds(accountId));
    }

    /** 全量覆盖指定管理员拥有的角色。 */
    @PostMapping("/assign-roles/{accountId}")
    @SaCheckPermission("system:user:assign-role")
    public ApiResponse<Void> assignRoles(@PathVariable Long accountId,
                                         @Valid @RequestBody AssignIdsRequest request) {
        adminRoleService.assignAccountRoles(accountId, request.ids());
        return ApiResponse.success(null);
    }
}
