package com.lingxi.workbench.admin.auth.infrastructure.web;

import com.lingxi.workbench.admin.auth.AdminUserContext;
import com.lingxi.workbench.admin.auth.domain.AdminAccount;
import com.lingxi.workbench.admin.auth.domain.AdminAccountRepository;
import com.lingxi.workbench.framework.auth.AuthException;
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
    private final AdminAccountRepository adminAccountRepository;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        adminStpLogic.checkLogin();
        AdminAccount account = adminAccountRepository.findById(adminStpLogic.getLoginIdAsLong()).orElseThrow(AuthException::accountNotFound);
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
