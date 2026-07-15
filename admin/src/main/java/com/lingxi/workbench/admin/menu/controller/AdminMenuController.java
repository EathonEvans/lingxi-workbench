package com.lingxi.workbench.admin.menu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.menu.model.vo.AdminMenuVO;
import com.lingxi.workbench.admin.menu.service.AdminMenuService;
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

/**
 * 管理端菜单和功能权限接口。
 */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    /** 查询完整菜单树。 */
    @GetMapping("/tree")
    @SaCheckPermission("system:menu:list")
    public ApiResponse<List<AdminMenuVO>> listTree() {
        return ApiResponse.success(adminMenuService.listTree());
    }

    /** 新增菜单、目录或按钮权限。 */
    @PostMapping("/create")
    @SaCheckPermission("system:menu:create")
    public ApiResponse<AdminMenuVO> create(@Valid @RequestBody CreateMenuRequest request) {
        return ApiResponse.success(adminMenuService.create(request));
    }

    /** 修改菜单、目录或按钮权限。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("system:menu:update")
    public ApiResponse<AdminMenuVO> update(@PathVariable Long id, @Valid @RequestBody UpdateMenuRequest request) {
        return ApiResponse.success(adminMenuService.update(id, request));
    }

    /** 删除没有子节点且没有角色引用的菜单。 */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("system:menu:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminMenuService.delete(id);
        return ApiResponse.success(null);
    }
}
