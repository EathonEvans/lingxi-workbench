package com.lingxi.workbench.auth.config.interceptor;

import com.lingxi.workbench.auth.context.UserContext;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.repo.UserRepo;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.context.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户端接口登录校验与用户上下文初始化拦截器。
 */
@Slf4j
@Component
public class UserAuthenticationInterceptor implements HandlerInterceptor {

    private final StpLogicDefinition stpLogicDefinition;
    private final UserRepo userRepo;

    public UserAuthenticationInterceptor(StpLogicDefinition stpLogicDefinition, UserRepo userRepo) {
        this.stpLogicDefinition = stpLogicDefinition;
        this.userRepo = userRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginType = stpLogicDefinition.getLoginType();
        log.info("当前用户loginType：{}", loginType);
        UserBO user = userRepo.findById(stpLogicDefinition.getLoginIdAsLong()).orElseThrow(AuthException::userNotFound);
        if (!user.isActive()) {
            throw AuthException.userDisabled("账号已停用，请联系管理员");
        }
        if (!user.canAccessApplication()) {
            throw AuthException.userDisabled("应用端访问权限已关闭，请联系管理员");
        }

        UserContext.set(new LoginUser(user.id(), user.username(), user.displayName(), user.avatarUrl()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        UserContext.clear();
    }
}
