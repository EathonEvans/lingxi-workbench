package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.RuntimeMcpAuthenticationType;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeMcpServerEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.mcp.AgentScopeMcpClientFactory;
import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将平台 Tool 定义适配为一次短生命周期的 MCP 工具调用。
 *
 * <p>每次调用重新读取本次装配的连接配置并建立客户端，调用结束立即关闭。
 * 这样停用、地址修改和密钥轮换不会被 JVM 长连接缓存掩盖，也不会泄漏客户端资源。</p>
 */
public class McpRuntimeToolExecutor implements AgentTool {

    private final RuntimeToolEntity tool;
    private final RuntimeMcpServerEntity server;
    private final AgentScopeMcpClientFactory clientFactory;
    private final SecretCipher secretCipher;

    public McpRuntimeToolExecutor(RuntimeToolEntity tool, RuntimeMcpServerEntity server,
                                  AgentScopeMcpClientFactory clientFactory, SecretCipher secretCipher) {
        this.tool = tool;
        this.server = server;
        this.clientFactory = clientFactory;
        this.secretCipher = secretCipher;
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.builder()
                .code(tool.getCode())
                .displayName(tool.getName())
                .description(tool.getDescription())
                .build();
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new LinkedHashMap<>(tool.getParameterSchema());
    }

    @Override
    public String execute(AgentToolRequest request) {
        return invoke(tool.getRemoteToolName(), request.input(), toMcpMeta(request));
    }

    /** 同一受信任 MCP Server 的对账查询入口，不会向模型暴露该远端 Tool。 */
    String callRemoteTool(String remoteToolName, AgentToolRequest request) {
        if (remoteToolName == null || remoteToolName.isBlank()) {
            throw new AgentToolException("MCP对账查询Tool名称不能为空：" + tool.getCode());
        }
        return invoke(remoteToolName, request.input(), toMcpMeta(request));
    }

    /**
     * 这是 Application 内部的单次 MCP 执行桥接；AgentScope Toolkit 直接通过稳定 AgentTool 契约调用。
     */
    private String invoke(String remoteToolName, String toolInput, Map<String, Object> mcpMeta) {
        McpClientConnectionConfig connectionConfig = McpClientConnectionConfig.builder()
                .baseUrl(server.getBaseUrl())
                .endpoint(server.getEndpoint())
                .bearerToken(resolveBearerToken())
                .requestTimeoutSeconds(server.getRequestTimeoutSeconds())
                .build();
        try (McpClientWrapper client = clientFactory.openInitialized("lingxi-" + server.getCode(), connectionConfig)) {
            McpSchema.CallToolResult result = mcpMeta.isEmpty()
                    ? client.callTool(remoteToolName, parseArguments(toolInput)).block()
                    : client.callTool(remoteToolName, parseArguments(toolInput), mcpMeta).block();
            if (Objects.isNull(result)) {
                throw new AgentToolException("MCP工具调用未返回结果：server=" + server.getCode() + "，tool=" + tool.getCode());
            }
            if (Boolean.TRUE.equals(result.isError())) {
                throw new AgentToolException("MCP工具执行失败：server=" + server.getCode() + "，tool=" + tool.getCode());
            }
            return JSON.toJSONString(result.content());
        } catch (AgentToolException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AgentToolException("MCP工具调用失败：server=" + server.getCode() + "，tool=" + tool.getCode(), exception);
        }
    }

    private static Map<String, Object> parseArguments(String toolInput) {
        if (Objects.isNull(toolInput) || toolInput.isBlank()) {
            return Map.of();
        }
        JSONObject arguments = JSON.parseObject(toolInput);
        if (Objects.isNull(arguments)) {
            return Map.of();
        }
        return new LinkedHashMap<>(arguments);
    }

    /** 只将平台定义的可信执行元数据透传给远端 MCP Server。 */
    private static Map<String, Object> toMcpMeta(AgentToolRequest request) {
        AgentToolExecutionContext execution = request.execution();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("executionId", execution.getExecutionId());
        metadata.put("userId", execution.getUserId());
        metadata.put("conversationId", execution.getConversationId());
        metadata.put("assistantMessageId", execution.getAssistantMessageId());
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            metadata.put("idempotencyKey", request.idempotencyKey());
        }
        return metadata;
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
