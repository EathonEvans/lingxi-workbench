package com.lingxi.workbench.application.runtime.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.RuntimeMcpAuthenticationType;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeMcpServerEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.mcp.AgentScopeMcpClientFactory;
import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.LinkedHashMap;
import java.util.Map;
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
    private final AgentScopeMcpClientFactory clientFactory;
    private final SecretCipher secretCipher;

    public McpRuntimeToolCallback(RuntimeToolEntity tool, RuntimeMcpServerEntity server,
                                  AgentScopeMcpClientFactory clientFactory, SecretCipher secretCipher) {
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
        return invoke(tool.getRemoteToolName(), toolInput, Map.of());
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return invoke(tool.getRemoteToolName(), toolInput, toMcpMeta(toolContext));
    }

    /** 同一受信任 MCP Server 的对账查询入口，不会向模型暴露该远端 Tool。 */
    String callRemoteTool(String remoteToolName, String toolInput, ToolContext toolContext) {
        if (remoteToolName == null || remoteToolName.isBlank()) {
            throw new AgentToolException("MCP对账查询Tool名称不能为空：" + tool.getCode());
        }
        return invoke(remoteToolName, toolInput, toMcpMeta(toolContext));
    }

    /**
     * 这是 Application 内部的单次 MCP 执行桥接；AgentScope Toolkit 只通过 Runtime Tool 适配器调用它，
     * 不会把该 Spring AI 回调注册进 AgentScope 或重新启用 Spring AI Tool Calling 循环。
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

    /** 与被替换的 Spring AI MCP 回调一致，不将 Web 交换对象透传给远端 MCP Server。 */
    private static Map<String, Object> toMcpMeta(ToolContext toolContext) {
        if (Objects.isNull(toolContext) || Objects.isNull(toolContext.getContext()) || toolContext.getContext().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        toolContext.getContext().forEach((key, value) -> {
            if (!"exchange".equals(key) && Objects.nonNull(value)) {
                metadata.put(key, value);
            }
        });
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
