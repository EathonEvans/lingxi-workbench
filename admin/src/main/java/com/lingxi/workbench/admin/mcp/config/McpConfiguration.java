package com.lingxi.workbench.admin.mcp.config;

import com.lingxi.workbench.infra.ai.mcp.McpServerAddressPolicy;
import com.lingxi.workbench.infra.ai.mcp.McpSyncClientFactory;
import com.lingxi.workbench.infra.ai.mcp.config.McpClientSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 控制面配置入口。
 */
@Configuration
@EnableConfigurationProperties(McpClientSecurityProperties.class)
public class McpConfiguration {

    @Bean
    public McpServerAddressPolicy mcpServerAddressPolicy(McpClientSecurityProperties properties) {
        return new McpServerAddressPolicy(properties);
    }

    @Bean
    public McpSyncClientFactory mcpSyncClientFactory(McpServerAddressPolicy addressPolicy) {
        return new McpSyncClientFactory(addressPolicy);
    }
}
