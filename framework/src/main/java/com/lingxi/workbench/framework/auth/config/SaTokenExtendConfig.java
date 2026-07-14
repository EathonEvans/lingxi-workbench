package com.lingxi.workbench.framework.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties("sa-token.extend")
public class SaTokenExtendConfig {
    /**
     * 用于隔离不同服务的认证会话。
     */
    private String loginType;

    /**
     * 不进行身份认证的接口路径。
     */
    private List<String> excludeUri = List.of();

}
