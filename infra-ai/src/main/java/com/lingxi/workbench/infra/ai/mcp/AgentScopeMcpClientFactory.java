package com.lingxi.workbench.infra.ai.mcp;

import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Objects;

/**
 * 基于 AgentScope 创建短生命周期 Streamable HTTP MCP 客户端。
 *
 * <p>调用方必须使用 try-with-resources 关闭返回的客户端。工厂不缓存连接，
 * 因此数据库中的停用、密钥轮换和地址变更会在下一次调用时立即生效。</p>
 */
@RequiredArgsConstructor
public class AgentScopeMcpClientFactory {

    private final McpServerAddressPolicy addressPolicy;

    public McpClientWrapper openInitialized(String clientName, McpClientConnectionConfig config) {
        addressPolicy.validateConfiguration(config.getBaseUrl(), config.getEndpoint());
        addressPolicy.validateOutboundAddress(config.getBaseUrl());

        Duration timeout = Duration.ofSeconds(config.getRequestTimeoutSeconds());
        McpClientBuilder builder = McpClientBuilder.create(clientName)
                .streamableHttpTransport(resolveTransportUrl(config.getBaseUrl(), config.getEndpoint()))
                .timeout(timeout)
                .initializationTimeout(timeout);
        if (Objects.nonNull(config.getBearerToken()) && !config.getBearerToken().isBlank()) {
            builder.header("Authorization", "Bearer " + config.getBearerToken());
        }

        McpClientWrapper client = builder.buildSync();
        try {
            client.initialize().block();
            return client;
        } catch (RuntimeException exception) {
            try {
                client.close();
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private static String resolveTransportUrl(String baseUrl, String endpoint) {
        return baseUrl.trim().replaceAll("/+$", "") + endpoint.trim();
    }
}
