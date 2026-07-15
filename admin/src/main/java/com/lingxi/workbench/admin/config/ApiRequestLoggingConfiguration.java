package com.lingxi.workbench.admin.config;

import com.lingxi.workbench.framework.web.logging.ApiRequestLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 管理端接口日志配置。
 */
@Configuration(proxyBeanMethods = false)
public class ApiRequestLoggingConfiguration {

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
