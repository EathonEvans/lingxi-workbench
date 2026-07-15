package com.lingxi.workbench.admin.role.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.role.model.request.AssignIdsRequest;
import com.lingxi.workbench.admin.role.model.request.CreateRoleRequest;
import com.lingxi.workbench.admin.role.model.request.UpdateRoleRequest;
import com.lingxi.workbench.admin.role.model.vo.AdminRoleVO;
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

import java.util.List;
import java.util.Set;

/**
 * 管理端角色管理 API。
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    /** 查询全部角色。 */
    @GetMapping("/list")
    @SaCheckPermission("system:role:list")
    public ApiResponse<List<AdminRoleVO>> list() {
        return ApiResponse.success(adminRoleService.list());
    }

    /** 新增业务角色。 */
    @PostMapping("/create")
    @SaCheckPermission("system:role:create")
    public ApiResponse<AdminRoleVO> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.success(adminRoleService.create(request));
    }

    /** 修改角色基础信息和数据范围类型。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("system:role:update")
    public ApiResponse<AdminRoleVO> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.success(adminRoleService.update(id, request));
    }

    /** 删除未分配给管理员的非内置角色。 */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("system:role:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminRoleService.delete(id);
        return ApiResponse.success(null);
    }

    /** 查询角色当前显式勾选的菜单主键，用于分配菜单弹窗回显。 */
    @GetMapping("/list-menu-ids/{id}")
    @SaCheckPermission("system:role:assign-menu")
    public ApiResponse<Set<Long>> listMenuIds(@PathVariable Long id) {
        return ApiResponse.success(adminRoleService.listRoleMenuIds(id));
    }

    /** 查询角色当前勾选的自定义数据范围部门主键。 */
    @GetMapping("/list-department-ids/{id}")
    @SaCheckPermission("system:role:assign-department")
    public ApiResponse<Set<Long>> listDepartmentIds(@PathVariable Long id) {
        return ApiResponse.success(adminRoleService.listRoleDepartmentIds(id));
    }

    /**
     * 全量覆盖角色的菜单和按钮权限。
     * 请求中的 ids 为空集合时表示清空当前角色的全部菜单权限。
     */
    @PostMapping("/assign-menus/{id}")
    @SaCheckPermission("system:role:assign-menu")
    public ApiResponse<Void> assignMenus(@PathVariable Long id,
                                          @Valid @RequestBody AssignIdsRequest request) {
        adminRoleService.assignMenus(id, request.ids());
        return ApiResponse.success(null);
    }

    /** 全量覆盖自定义数据范围角色可访问的部门。 */
    @PostMapping("/assign-departments/{id}")
    @SaCheckPermission("system:role:assign-department")
    public ApiResponse<Void> assignDepartments(@PathVariable Long id,
                                                @Valid @RequestBody AssignIdsRequest request) {
        adminRoleService.assignDepartments(id, request.ids());
        return ApiResponse.success(null);
    }
}
