package com.lingxi.workbench.admin.auth.controller;

import com.lingxi.workbench.admin.auth.model.request.AdminLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.AdminLoginVO;
import com.lingxi.workbench.admin.auth.service.AdminLoginService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AdminLoginController {

    private final AdminLoginService adminLoginService;

    public AdminLoginController(AdminLoginService adminLoginService) {
        this.adminLoginService = adminLoginService;
    }

    /**
     * 账号密码登录
     *
     * @param request 请求参数
     * @return 响应结果
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(adminLoginService.login(request));
    }
}
