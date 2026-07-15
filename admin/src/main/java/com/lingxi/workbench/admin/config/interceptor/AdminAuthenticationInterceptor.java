package com.lingxi.workbench.admin.config.interceptor;

import com.lingxi.workbench.admin.auth.context.AdminUserContext;
import com.lingxi.workbench.admin.auth.model.bo.AdminAccountBO;
import com.lingxi.workbench.admin.auth.repo.AdminAccountRepo;
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
public class AdminAuthenticationInterceptor implements HandlerInterceptor {

    private final StpLogicDefinition adminStpLogic;
    private final AdminAccountRepo adminAccountRepo;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        adminStpLogic.checkLogin();
        AdminAccountBO account = adminAccountRepo.findById(adminStpLogic.getLoginIdAsLong()).orElseThrow(AuthException::accountNotFound);
        if (!account.isActive()) {
            throw AuthException.accountDisabled("管理员账号已停用");
        }

        AdminUserContext.set(new LoginUser(account.id(), account.username(), account.displayName(), account.avatarUrl()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        AdminUserContext.clear();
    }
}
