package com.lingxi.workbench.admin.auth.api;

import com.lingxi.workbench.admin.auth.application.AdminLoginCommand;
import com.lingxi.workbench.admin.auth.application.AdminLoginResult;
import com.lingxi.workbench.admin.auth.application.AdminLoginService;
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
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminLoginResult result = adminLoginService.login(new AdminLoginCommand(request.username(), request.password()));
        return ApiResponse.success(AdminLoginResponse.from(result));
    }
}
