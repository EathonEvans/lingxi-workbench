package com.lingxi.workbench.admin.mcp.config;

import com.lingxi.workbench.infra.ai.mcp.McpServerAddressPolicy;
import com.lingxi.workbench.infra.ai.mcp.AgentScopeMcpClientFactory;
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
    public AgentScopeMcpClientFactory agentScopeMcpClientFactory(McpServerAddressPolicy addressPolicy) {
        return new AgentScopeMcpClientFactory(addressPolicy);
    }
}
