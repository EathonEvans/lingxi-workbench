package com.lingxi.workbench.auth.service;

import com.lingxi.workbench.auth.model.request.UserLoginRequest;
import com.lingxi.workbench.auth.model.vo.UserLoginVO;

/**
 * 应用端用户登录服务接口。
 */
public interface UserLoginService {

    /**
     * 执行用户登录：校验账号密码、账号状态与应用端访问权限，成功后返回令牌。
     *
     * @param request 登录请求参数
     * @return 包含 Sa-Token 令牌与用户资料的响应
     */
    UserLoginVO login(UserLoginRequest request);
}
