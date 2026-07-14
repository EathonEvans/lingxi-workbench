package com.lingxi.workbench.auth.infrastructure.web;

import com.lingxi.workbench.auth.UserContext;
import com.lingxi.workbench.auth.domain.UserAccount;
import com.lingxi.workbench.auth.domain.UserAccountRepository;
import com.lingxi.workbench.framework.auth.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.context.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户端接口登录校验与用户上下文初始化拦截器。
 */
@Component
public class UserAuthenticationInterceptor implements HandlerInterceptor {

    private final StpLogicDefinition stpLogicDefinition;
    private final UserAccountRepository userAccountRepository;

    public UserAuthenticationInterceptor(StpLogicDefinition stpLogicDefinition, UserAccountRepository userAccountRepository) {
        this.stpLogicDefinition = stpLogicDefinition;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        stpLogicDefinition.checkLogin();
        UserAccount account = userAccountRepository.findById(stpLogicDefinition.getLoginIdAsLong()).orElseThrow(AuthException::accountNotFound);
        if (!account.isActive()) {
            throw AuthException.accountDisabled("账号已停用，请联系管理员");
        }

        UserContext.set(new LoginUser(account.id(), account.username(), account.displayName(), account.avatarUrl()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        UserContext.clear();
    }
}
