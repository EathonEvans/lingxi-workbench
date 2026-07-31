package com.lingxi.workbench.infra.ai.mcp;

import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Objects;

import static com.lingxi.workbench.infra.ai.mcp.common.constants.McpClientConstants.CLIENT_NAME;
import static com.lingxi.workbench.infra.ai.mcp.common.constants.McpClientConstants.CLIENT_VERSION;

/**
 * 动态创建并初始化 Streamable HTTP MCP 同步客户端。
 *
 * <p>调用方必须使用 try-with-resources 关闭返回的客户端。工厂不缓存连接，
 * 因此数据库中的停用、密钥轮换和地址变更会在下一次调用时立即生效。</p>
 */
@RequiredArgsConstructor
public class McpSyncClientFactory {

    private final McpServerAddressPolicy addressPolicy;

    public McpSyncClient openInitialized(McpClientConnectionConfig config) {
        addressPolicy.validateConfiguration(config.getBaseUrl(), config.getEndpoint());
        addressPolicy.validateOutboundAddress(config.getBaseUrl());

        Duration timeout = Duration.ofSeconds(config.getRequestTimeoutSeconds());
        HttpClientStreamableHttpTransport.Builder transportBuilder = HttpClientStreamableHttpTransport.builder(normalizeBaseUrl(config.getBaseUrl()))
                .endpoint(config.getEndpoint().trim())
                .connectTimeout(timeout);
        if (Objects.nonNull(config.getBearerToken()) && !config.getBearerToken().isBlank()) {
            transportBuilder.httpRequestCustomizer((request, method, uri, body, context) -> request.timeout(timeout).header("Authorization", "Bearer " + config.getBearerToken()));
        } else {
            transportBuilder.httpRequestCustomizer((request, method, uri, body, context) -> request.timeout(timeout));
        }

        McpSyncClient client = McpClient.sync(transportBuilder.build())
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .clientInfo(McpSchema.Implementation.builder(CLIENT_NAME, CLIENT_VERSION).build())
                .build();
        try {
            client.initialize();
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

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.trim().replaceAll("/+$", "");
    }
}
