package com.lingxi.workbench.admin.auth.service;

import com.lingxi.workbench.admin.auth.model.request.SysLoginRequest;
import com.lingxi.workbench.admin.auth.model.vo.SysLoginVO;

/**
 * 管理端登录服务接口，负责校验账号密码并签发令牌。
 */
public interface SysLoginService {

    /**
     * 校验账号密码并完成登录，返回 Sa-Token 令牌与管理员基础资料。
     *
     * @param request 登录请求参数
     * @return 登录响应，包含令牌和管理员信息
     * @throws com.lingxi.workbench.framework.exceptions.AuthException 账号不存在、密码错误或账号已停用时抛出
     */
    SysLoginVO login(SysLoginRequest request);
}
