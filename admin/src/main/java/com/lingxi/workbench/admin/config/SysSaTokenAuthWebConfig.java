/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lingxi.workbench.admin.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.admin.config.interceptor.SysAuthenticationInterceptor;
import com.lingxi.workbench.framework.auth.StpLogicDefinition;
import com.lingxi.workbench.framework.auth.config.SaTokenExtendConfig;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SaToken 配置类
 * 配置登录拦截和用户上下文拦截器
 */
@Configuration
@RequiredArgsConstructor
public class SysSaTokenAuthWebConfig implements WebMvcConfigurer {

    /**
     * 用户上下文拦截器
     */
    private final SysAuthenticationInterceptor authenticationInterceptor;
    private final SaTokenExtendConfig saTokenExtendConfig;
    private final StpLogicDefinition stpLogicDefinition;


    /**
     * 添加拦截器配置
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // SaInterceptor 专门解析 @SaCheckPermission、@SaCheckRole 等方法注解。
        // 全局过滤器只负责登录态校验，二者职责不同，不能只注册其中一个。
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(saTokenExtendConfig.getExcludeUri());

        // 注册用户上下文拦截器
        registry.addInterceptor(authenticationInterceptor)
                // 拦截所有路径
                .addPathPatterns("/**")
                // 排除认证相关路径和错误页面
                .excludePathPatterns(saTokenExtendConfig.getExcludeUri());
    }

    /**
     * 注册 [Sa-Token 全局过滤器]
     */
    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                // 指定 [拦截路由] 与 [放行路由]
                .addInclude("/**")
                .setExcludeList(saTokenExtendConfig.getExcludeUri())
                // 认证函数: 每次请求执行
                // 禁止在日志中记录令牌原文，避免认证凭证进入日志系统。
                .setAuth(obj -> stpLogicDefinition.checkLogin())
                // SaServletFilter 不经过 Spring MVC 的消息转换器，需要在此显式设置 HTTP 状态并序列化 JSON。
                .setError(this::writeUnauthorizedResponse)
                // 前置函数：在每次认证函数之前执行
                .setBeforeAuth(obj -> {
                    SaHolder.getResponse()
                            // 允许指定域访问跨域资源
                            .setHeader("Access-Control-Allow-Origin", "*")
                            // 允许所有请求方式
                            .setHeader("Access-Control-Allow-Methods", "*")
                            // 允许的header参数
                            .setHeader("Access-Control-Allow-Headers", "*")
                            // 有效时间
                            .setHeader("Access-Control-Max-Age", "3600");

                    // 如果是预检请求，则立即返回到前端
                    SaRouter.match(SaHttpMethod.OPTIONS)
                            .free(r -> {
                                // 预检请求只需要补充跨域响应头，不进入登录和业务权限校验。
                            })
                            .back();
                });
    }

    /**
     * 将过滤器阶段的登录异常转换为统一 JSON 响应。
     *
     * <p>这里不直接返回 ApiResponse 对象，否则 Sa-Token 会调用其 toString()，最终产生
     * HTTP 200 和 text/plain，前端便无法触发统一的 401 处理逻辑。</p>
     */
    private String writeUnauthorizedResponse(Throwable exception) {
        SaHolder.getResponse()
                .setStatus(HttpStatusEnum.UNAUTHORIZED.getCode())
                .setHeader("Content-Type", "application/json;charset=UTF-8");
        return JSON.toJSONString(ApiResponse.failure(HttpStatusEnum.UNAUTHORIZED, "登录状态无效，请重新登录"));
    }
}
