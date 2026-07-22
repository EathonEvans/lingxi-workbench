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
 *
 * <p>提供菜单树的查询、新增、修改及删除接口，各接口均受 Sa-Token 权限控制。</p>
 */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /**
     * 查询完整菜单树，包含停用节点和按钮节点。
     *
     * @return 菜单树根节点列表
     */
    @GetMapping("/tree")
    @SaCheckPermission("system:menu:list")
    public ApiResponse<List<SysMenuVO>> listTree() {
        return ApiResponse.success(sysMenuService.listTree());
    }

    /**
     * 新增菜单、目录或按钮权限。
     *
     * @param request 新增菜单请求
     * @return 新增后的菜单视图
     */
    @PostMapping("/create")
    @SaCheckPermission("system:menu:create")
    public ApiResponse<SysMenuVO> create(@Valid @RequestBody CreateMenuRequest request) {
        return ApiResponse.success(sysMenuService.create(request));
    }

    /**
     * 修改菜单、目录或按钮权限。
     *
     * @param id      菜单主键
     * @param request 修改请求
     * @return 修改后的菜单视图
     */
    @PostMapping("/update/{id}")
    @SaCheckPermission("system:menu:update")
    public ApiResponse<SysMenuVO> update(@PathVariable Long id, @Valid @RequestBody UpdateMenuRequest request) {
        return ApiResponse.success(sysMenuService.update(id, request));
    }

    /**
     * 删除菜单。
     *
     * <p>仅当无子节点且未被角色引用时允许删除。</p>
     *
     * @param id 菜单主键
     * @return 空响应体
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("system:menu:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return ApiResponse.success(null);
    }
}
