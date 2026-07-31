package com.lingxi.workbench.application.runtime.config;

import com.lingxi.workbench.infra.ai.mcp.McpServerAddressPolicy;
import com.lingxi.workbench.infra.ai.mcp.McpSyncClientFactory;
import com.lingxi.workbench.infra.ai.mcp.config.McpClientSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application 动态 MCP 客户端配置入口。
 */
@Configuration
@EnableConfigurationProperties(McpClientSecurityProperties.class)
public class McpRuntimeConfiguration {

    @Bean
    public McpServerAddressPolicy mcpServerAddressPolicy(McpClientSecurityProperties properties) {
        return new McpServerAddressPolicy(properties);
    }

    @Bean
    public McpSyncClientFactory mcpSyncClientFactory(McpServerAddressPolicy addressPolicy) {
        return new McpSyncClientFactory(addressPolicy);
    }
}
