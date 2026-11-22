package com.lingxi.workbench.auth.controller;

import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;
import com.lingxi.workbench.auth.service.UserLoginService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用端用户登录控制器。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class UserLoginController {

    private final UserLoginService userLoginService;

    public UserLoginController(UserLoginService userLoginService) {
        this.userLoginService = userLoginService;
    }

    /**
     * 用户登录接口，校验账号密码后返回 Sa-Token 令牌及用户资料。
     *
     * @param request 登录请求参数
     * @return 包含令牌与用户资料的统一响应
     */
    @PostMapping("/login")
    public ApiResponse<UserLoginVO> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(userLoginService.login(request));
    }
}
