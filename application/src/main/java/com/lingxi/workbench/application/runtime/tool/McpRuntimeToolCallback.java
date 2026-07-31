package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.application.runtime.common.enums.RuntimeMcpAuthenticationType;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeMcpServerEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.mcp.McpSyncClientFactory;
import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Objects;

/**
 * 将平台 Tool 定义适配为一次短生命周期的 MCP 工具调用。
 *
 * <p>每次调用重新读取本次装配的连接配置并建立客户端，调用结束立即关闭。
 * 这样停用、地址修改和密钥轮换不会被 JVM 长连接缓存掩盖，也不会泄漏客户端资源。</p>
 */
public class McpRuntimeToolCallback implements ToolCallback {

    private final RuntimeToolEntity tool;
    private final RuntimeMcpServerEntity server;
    private final McpSyncClientFactory clientFactory;
    private final SecretCipher secretCipher;

    public McpRuntimeToolCallback(RuntimeToolEntity tool, RuntimeMcpServerEntity server, McpSyncClientFactory clientFactory, SecretCipher secretCipher) {
        this.tool = tool;
        this.server = server;
        this.clientFactory = clientFactory;
        this.secretCipher = secretCipher;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(tool.getCode())
                .description(tool.getDescription())
                .inputSchema(tool.getParameterSchema().toJSONString())
                .build();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().build();
    }

    @Override
    public String call(String toolInput) {
        return invoke(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return invoke(toolInput, toolContext);
    }

    private String invoke(String toolInput, ToolContext toolContext) {
        McpClientConnectionConfig connectionConfig = McpClientConnectionConfig.builder()
                .baseUrl(server.getBaseUrl())
                .endpoint(server.getEndpoint())
                .bearerToken(resolveBearerToken())
                .requestTimeoutSeconds(server.getRequestTimeoutSeconds())
                .build();
        try (McpSyncClient client = clientFactory.openInitialized(connectionConfig)) {
            McpSchema.Tool remoteTool = McpSchema.Tool.builder(tool.getRemoteToolName(), tool.getParameterSchema())
                    .description(tool.getDescription())
                    .build();
            SyncMcpToolCallback delegate = SyncMcpToolCallback.builder()
                    .mcpClient(client)
                    .tool(remoteTool)
                    .prefixedToolName(tool.getCode())
                    .build();
            if (Objects.isNull(toolContext)) {
                return delegate.call(toolInput);
            }
            return delegate.call(toolInput, toolContext);
        } catch (RuntimeException exception) {
            throw new AgentToolException("MCP工具调用失败：server=" + server.getCode() + "，tool=" + tool.getCode(), exception);
        }
    }

    private String resolveBearerToken() {
        if (server.getAuthenticationType() != RuntimeMcpAuthenticationType.BEARER_TOKEN) {
            return null;
        }
        if (Objects.isNull(server.getSecretCiphertext()) || Objects.isNull(server.getSecretIv())) {
            throw new AgentToolException("MCP Server认证配置不完整：" + server.getCode());
        }
        return secretCipher.decrypt(server.getSecretCiphertext(), server.getSecretIv());
    }
}
