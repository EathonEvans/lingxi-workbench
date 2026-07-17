package com.lingxi.workbench.admin.sys.menu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.sys.menu.model.request.CreateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.request.UpdateMenuRequest;
import com.lingxi.workbench.admin.sys.menu.model.vo.SysMenuVO;
import com.lingxi.workbench.admin.sys.menu.service.SysMenuService;
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
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /** 查询完整菜单树。 */
    @GetMapping("/tree")
    @SaCheckPermission("system:menu:list")
    public ApiResponse<List<SysMenuVO>> listTree() {
        return ApiResponse.success(sysMenuService.listTree());
    }

    /** 新增菜单、目录或按钮权限。 */
    @PostMapping("/create")
    @SaCheckPermission("system:menu:create")
    public ApiResponse<SysMenuVO> create(@Valid @RequestBody CreateMenuRequest request) {
        return ApiResponse.success(sysMenuService.create(request));
    }

    /** 修改菜单、目录或按钮权限。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("system:menu:update")
    public ApiResponse<SysMenuVO> update(@PathVariable Long id, @Valid @RequestBody UpdateMenuRequest request) {
        return ApiResponse.success(sysMenuService.update(id, request));
    }

    /** 删除没有子节点且没有角色引用的菜单。 */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("system:menu:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return ApiResponse.success(null);
    }
}
