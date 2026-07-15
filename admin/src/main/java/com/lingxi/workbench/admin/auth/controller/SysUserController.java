package com.lingxi.workbench.admin.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.auth.model.request.CreateSysUserRequest;
import com.lingxi.workbench.admin.auth.model.request.UpdateSysUserRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysUserVO;
import com.lingxi.workbench.admin.auth.service.SysUserService;
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

/** 系统用户管理 API。 */
@RestController
@RequestMapping("/api/v1/sys-users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /** 查询全部系统用户。 */
    @GetMapping("/list")
    @SaCheckPermission("system:user:list")
    public ApiResponse<List<SysUserVO>> list() {
        return ApiResponse.success(sysUserService.list());
    }

    /** 新增系统用户。 */
    @PostMapping("/create")
    @SaCheckPermission("system:user:create")
    public ApiResponse<SysUserVO> create(@Valid @RequestBody CreateSysUserRequest request) {
        return ApiResponse.success(sysUserService.create(request));
    }

    /** 修改系统用户资料、启停状态和应用端访问资格。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("system:user:update")
    public ApiResponse<SysUserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateSysUserRequest request) {
        return ApiResponse.success(sysUserService.update(id, request));
    }
}
