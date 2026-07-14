package com.lingxi.workbench.auth.api;

import com.lingxi.workbench.auth.application.UserLoginCommand;
import com.lingxi.workbench.auth.application.UserLoginResult;
import com.lingxi.workbench.auth.application.UserLoginService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class UserLoginController {

    private final UserLoginService userLoginService;

    public UserLoginController(UserLoginService userLoginService) {
        this.userLoginService = userLoginService;
    }

    @PostMapping("/login")
    public ApiResponse<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        UserLoginResult result = userLoginService.login(
                new UserLoginCommand(request.username(), request.password())
        );
        return ApiResponse.success(UserLoginResponse.from(result));
    }
}
