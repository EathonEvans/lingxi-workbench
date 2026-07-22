package com.lingxi.workbench.config;

import com.lingxi.workbench.framework.web.logging.ApiRequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 用户端接口日志配置。
 */
@Configuration(proxyBeanMethods = false)
public class ApiRequestLoggingConfiguration {

    /**
     * 注册接口请求日志过滤器，最高优先级拦截所有请求路径。
     *
     * @return 过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<ApiRequestLoggingFilter> apiRequestLoggingFilter() {
        FilterRegistrationBean<ApiRequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ApiRequestLoggingFilter());
        registration.setName("apiRequestLoggingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
