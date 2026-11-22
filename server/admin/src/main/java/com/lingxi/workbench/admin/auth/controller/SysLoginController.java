package com.lingxi.workbench.admin.auth.controller;

import com.lingxi.workbench.admin.auth.model.request.SysLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;
import com.lingxi.workbench.admin.auth.service.SysLoginService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端登录控制器，负责账号密码认证入口。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SysLoginController {

    private final SysLoginService sysLoginService;

    public SysLoginController(SysLoginService sysLoginService) {
        this.sysLoginService = sysLoginService;
    }

    /**
     * 账号密码登录
     *
     * @param request 请求参数
     * @return 响应结果
     */
    @PostMapping("/login")
    public ApiResponse<SysLoginVO> login(@Valid @RequestBody SysLoginRequest request) {
        return ApiResponse.success(sysLoginService.login(request));
    }
}
