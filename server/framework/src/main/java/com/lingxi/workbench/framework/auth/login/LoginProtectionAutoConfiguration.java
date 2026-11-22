package com.lingxi.workbench.framework.auth.login;

import com.lingxi.workbench.framework.cache.RedisUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 登录防爆破组件装配，依赖 framework 的可运行服务自动获得统一防护。
 */
@Configuration
@EnableConfigurationProperties(LoginProtectionProperties.class)
public class LoginProtectionAutoConfiguration {

    @Bean
    public LoginFailureGuard loginFailureGuard(RedisUtil redisUtil, LoginProtectionProperties properties) {
        return new LoginFailureGuard(redisUtil, properties);
    }
}
