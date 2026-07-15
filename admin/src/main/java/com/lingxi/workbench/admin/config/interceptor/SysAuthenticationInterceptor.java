package com.lingxi.workbench.admin.config.interceptor;

import com.lingxi.workbench.admin.auth.context.SysUserContext;
import com.lingxi.workbench.admin.auth.model.bo.SysUserBO;
import com.lingxi.workbench.admin.auth.repo.SysUserRepo;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.context.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端接口登录校验与用户上下文初始化拦截器。
 */
@Component
@RequiredArgsConstructor
public class SysAuthenticationInterceptor implements HandlerInterceptor {

    private final StpLogicDefinition stpLogicDefinition;
    private final SysUserRepo sysUserRepo;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        stpLogicDefinition.checkLogin();
        SysUserBO user = sysUserRepo.findById(stpLogicDefinition.getLoginIdAsLong())
                .orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.userDisabled("系统用户已停用");
        }
        if (!user.canAccessAdmin()) {
            throw AuthException.userDisabled("管理端访问权限已关闭");
        }

        SysUserContext.set(new LoginUser(user.id(), user.username(), user.displayName(), user.avatarUrl()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        SysUserContext.clear();
    }
}
