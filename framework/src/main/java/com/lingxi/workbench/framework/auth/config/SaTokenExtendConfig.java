package com.lingxi.workbench.framework.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Sa-Token 扩展配置，用于在同一进程中隔离不同服务的认证会话。
 *
 * <p>每个启动服务通过配置独立的 {@code loginType} 实现管理端与用户端会话隔离，
 * 并通过 {@code excludeUri} 声明无需身份认证的接口路径。</p>
 */
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
