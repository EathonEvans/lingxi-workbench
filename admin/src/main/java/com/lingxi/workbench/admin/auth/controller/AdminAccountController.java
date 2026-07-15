package com.lingxi.workbench.admin.auth.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.auth.model.request.CreateAdminAccountRequest;
import com.lingxi.workbench.admin.auth.model.request.UpdateAdminAccountRequest;
import com.lingxi.workbench.admin.auth.model.vo.AdminAccountVO;
import com.lingxi.workbench.admin.auth.service.AdminAccountService;
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

/** 管理端管理员账号目录 API。 */
@RestController
@RequestMapping("/api/v1/admin-users")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    /** 查询全部后台管理员账号。 */
    @GetMapping("/list")
    @SaCheckPermission("system:user:list")
    public ApiResponse<List<AdminAccountVO>> list() {
        return ApiResponse.success(adminAccountService.list());
    }

    /** 新增后台管理员账号。 */
    @PostMapping("/create")
    @SaCheckPermission("system:user:create")
    public ApiResponse<AdminAccountVO> create(@Valid @RequestBody CreateAdminAccountRequest request) {
        return ApiResponse.success(adminAccountService.create(request));
    }

    /** 修改后台管理员账号资料和启停状态。 */
    @PostMapping("/update/{id}")
    @SaCheckPermission("system:user:update")
    public ApiResponse<AdminAccountVO> update(@PathVariable Long id, @Valid @RequestBody UpdateAdminAccountRequest request) {
        return ApiResponse.success(adminAccountService.update(id, request));
    }
}
