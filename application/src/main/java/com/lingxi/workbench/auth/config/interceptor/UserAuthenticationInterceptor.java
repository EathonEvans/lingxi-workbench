package com.lingxi.workbench.auth.config.interceptor;

import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.repo.UserRepo;
import com.lingxi.workbench.framework.exceptions.AuthException;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.context.LoginUser;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 用户端接口登录校验与用户上下文初始化拦截器。
 */
@Component
@RequiredArgsConstructor
public class UserAuthenticationInterceptor implements AsyncHandlerInterceptor {

    private final StpLogicDefinition stpLogicDefinition;
    private final UserRepo userRepo;

    /**
     * 请求前置处理：校验登录态、账号状态与应用端访问权限，并初始化用户上下文。
     *
     * @return 校验通过返回 true，否则抛出异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE 等异步请求完成时会由容器发起 ASYNC 二次分派，此时 Sa-Token 的请求线程上下文已经释放。
        // 身份校验与用户上下文初始化只需要在首次 REQUEST 分派执行，异步分派直接放行。
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }

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

    /**
     * 请求进入异步处理后，原 Servlet 线程会立即归还线程池，必须同步清理用户上下文，避免线程复用污染。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
    }

    /**
     * 请求完成后清理用户上下文，防止线程复用污染。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        UserContext.clear();
    }
}
