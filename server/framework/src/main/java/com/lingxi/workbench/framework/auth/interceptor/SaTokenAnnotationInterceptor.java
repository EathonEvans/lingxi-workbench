package com.lingxi.workbench.framework.auth.interceptor;

import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 支持Servlet异步分派的Sa-Token注解拦截器。
 *
 * <p>SSE等异步请求在首次REQUEST分派时已经完成登录和权限校验。流结束后容器发起的
 * ASYNC二次分派不再拥有Sa-Token请求上下文，因此必须跳过重复的注解解析。</p>
 */
public class SaTokenAnnotationInterceptor extends SaInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        return super.preHandle(request, response, handler);
    }
}
