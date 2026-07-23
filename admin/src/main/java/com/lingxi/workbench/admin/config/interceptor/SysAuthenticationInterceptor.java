package com.lingxi.workbench.admin.config.interceptor;

import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.admin.sys.user.model.bo.SysUserBO;
import com.lingxi.workbench.admin.sys.user.repo.SysUserRepo;
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
 * 管理端接口登录校验与用户上下文初始化拦截器。
 */
@Component
@RequiredArgsConstructor
public class SysAuthenticationInterceptor implements AsyncHandlerInterceptor {

    private final StpLogicDefinition stpLogicDefinition;
    private final SysUserRepo sysUserRepo;


    /**
     * 请求前校验登录态，加载用户信息并初始化请求上下文。
     *
     * <p>校验顺序：登录态 -> 用户存在性 -> 启用状态 -> 管理端访问权限。</p>
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 异步二次分派沿用首次请求的认证结果，不重复读取已经释放的Sa-Token上下文。
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }

        // 校验 Sa-Token 登录态
        stpLogicDefinition.checkLogin();
        // 加载数据库用户信息
        SysUserBO user = sysUserRepo.findById(stpLogicDefinition.getLoginIdAsLong()).orElseThrow(AuthException::userNotFound);
        // 校验用户启用状态
        if (!user.isActive()) {
            throw AuthException.userDisabled("系统用户已停用");
        }
        // 校验管理端访问权限
        if (!user.canAccessAdmin()) {
            throw AuthException.userDisabled("管理端访问权限已关闭");
        }

        UserContext.set(new LoginUser(user.id(), user.username(), user.displayName(), user.avatarUrl()));
        return true;
    }

    /**
     * 请求进入异步处理后立即清理初始Servlet线程上的用户上下文，避免线程复用污染。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
    }

    /**
     * 请求完成后清理线程绑定的用户上下文。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        UserContext.clear();
    }
}
