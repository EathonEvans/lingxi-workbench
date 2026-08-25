package com.lingxi.workbench.admin.mcp.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import com.lingxi.workbench.admin.mcp.model.bo.McpConnectionConfig;
import com.lingxi.workbench.admin.mcp.model.bo.McpDiscoveredTool;
import com.lingxi.workbench.admin.mcp.model.bo.McpInspectionResult;
import com.lingxi.workbench.admin.mcp.service.McpConnectionClient;
import com.lingxi.workbench.infra.ai.mcp.AgentScopeMcpClientFactory;
import com.lingxi.workbench.infra.ai.mcp.McpClientSecurityException;
import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.CONNECTION_ERROR_CODE;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.INVALID_ADDRESS_ERROR_CODE;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.MAX_DISCOVERED_TOOL_COUNT;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.TIMEOUT_ERROR_CODE;

/**
 * 使用 AgentScope MCP Client 连接 Streamable HTTP Server 并发现工具。
 *
 * <p>管理端配置是动态数据，不能依赖 application.yml 静态自动配置客户端。
 * 每次测试或发现工具时创建短生命周期客户端，完成后立即关闭连接。</p>
 */
@Component
@RequiredArgsConstructor
public class AgentScopeMcpConnectionClient implements McpConnectionClient {

    private static final String INSPECTION_CLIENT_NAME = "lingxi-admin-mcp-inspection";

    private final AgentScopeMcpClientFactory clientFactory;

    @Override
    public McpInspectionResult inspect(McpConnectionConfig config) {
        long startedAt = System.nanoTime();
        try {
            return inspectRemote(config, startedAt);
        } catch (McpClientSecurityException exception) {
            return McpInspectionResult.failed(elapsedMillis(startedAt), INVALID_ADDRESS_ERROR_CODE, exception.getMessage());
        } catch (Exception exception) {
            return connectionFailure(exception, startedAt);
        }
    }

    private McpInspectionResult inspectRemote(McpConnectionConfig config, long startedAt) {
        McpClientConnectionConfig connectionConfig = McpClientConnectionConfig.builder()
                .baseUrl(config.getBaseUrl())
                .endpoint(config.getEndpoint())
                .bearerToken(config.getBearerToken())
                .requestTimeoutSeconds(config.getRequestTimeoutSeconds())
                .build();
        try (McpClientWrapper client = clientFactory.openInitialized(INSPECTION_CLIENT_NAME, connectionConfig)) {
            List<McpDiscoveredTool> tools = discoverTools(client);
            return McpInspectionResult.builder()
                    .status(McpConnectionTestStatus.SUCCESS)
                    .latencyMs(elapsedMillis(startedAt))
                    .message("连接成功")
                    .tools(tools)
                    .build();
        }
    }

    private static List<McpDiscoveredTool> discoverTools(McpClientWrapper client) {
        List<McpSchema.Tool> remoteTools = Objects.requireNonNullElse(client.listTools().block(), List.of());
        if (remoteTools.size() > MAX_DISCOVERED_TOOL_COUNT) {
            throw new IllegalStateException("MCP Server 工具数量超过平台发现上限");
        }
        return remoteTools.stream().map(AgentScopeMcpConnectionClient::toDiscoveredTool).toList();
    }

    private static McpDiscoveredTool toDiscoveredTool(McpSchema.Tool tool) {
        return McpDiscoveredTool.builder()
                .name(tool.name())
                .title(tool.title())
                .description(tool.description())
                .inputSchema(toJsonSchema(tool.inputSchema()))
                .build();
    }

    /** AgentScope MCP SDK 将工具入参 Schema 表达为值对象，控制面继续持久化 JSON 结构。 */
    private static JSONObject toJsonSchema(McpSchema.JsonSchema schema) {
        JSONObject result = new JSONObject();
        result.put("type", schema.type());
        result.put("properties", Objects.requireNonNullElse(schema.properties(), java.util.Map.of()));
        result.put("required", Objects.requireNonNullElse(schema.required(), List.of()));
        if (Objects.nonNull(schema.additionalProperties())) {
            result.put("additionalProperties", schema.additionalProperties());
        }
        if (Objects.nonNull(schema.defs()) && !schema.defs().isEmpty()) {
            result.put("$defs", schema.defs());
        }
        if (Objects.nonNull(schema.definitions()) && !schema.definitions().isEmpty()) {
            result.put("definitions", schema.definitions());
        }
        return result;
    }

    private static int elapsedMillis(long startedAt) {
        return Math.toIntExact(Math.min((System.nanoTime() - startedAt) / 1_000_000L, Integer.MAX_VALUE));
    }

    private static McpInspectionResult connectionFailure(Exception exception, long startedAt) {
        if (hasCause(exception, TimeoutException.class)) {
            return McpInspectionResult.failed(elapsedMillis(startedAt), TIMEOUT_ERROR_CODE, "连接 MCP Server 超时");
        }
        return McpInspectionResult.failed(elapsedMillis(startedAt), CONNECTION_ERROR_CODE, "无法连接 MCP Server，请检查地址、认证和服务状态");
    }

    private static boolean hasCause(Throwable source, Class<? extends Throwable> type) {
        Throwable current = source;
        while (Objects.nonNull(current)) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
