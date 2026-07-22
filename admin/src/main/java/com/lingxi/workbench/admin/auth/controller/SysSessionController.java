package com.lingxi.workbench.admin.auth.controller;

import com.lingxi.workbench.admin.auth.model.request.UpdateCurrentAvatarRequest;
import com.lingxi.workbench.admin.auth.service.SysPermissionService;
import com.lingxi.workbench.admin.sys.user.model.vo.SysAvatarVO;
import com.lingxi.workbench.admin.sys.user.model.vo.SysSessionVO;
import com.lingxi.workbench.admin.sys.user.service.SysUserService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前管理端登录会话 API。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class SysSessionController {

    private final SysPermissionService sysPermissionService;
    private final SysUserService sysUserService;

    /**
     * 获取当前管理员可访问的菜单、角色和功能权限。
     * 前端登录成功后调用该接口初始化动态路由，后续刷新页面也可重新拉取最新权限。
     */
    @GetMapping("/session")
    public ApiResponse<SysSessionVO> session() {
        Long userId = UserContext.userId();
        return ApiResponse.success(sysPermissionService.getSession(userId));
    }

    /** 修改当前管理员头像，不复用系统用户全量编辑接口。 */
    @PostMapping("/update-avatar")
    public ApiResponse<SysAvatarVO> updateAvatar(
            @Valid @RequestBody UpdateCurrentAvatarRequest request) {
        return ApiResponse.success(sysUserService.updateCurrentAvatar(request.avatarObjectKey()));
    }
}
