package com.lingxi.workbench.admin.mcp.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import com.lingxi.workbench.admin.mcp.model.bo.McpConnectionConfig;
import com.lingxi.workbench.admin.mcp.model.bo.McpDiscoveredTool;
import com.lingxi.workbench.admin.mcp.model.bo.McpInspectionResult;
import com.lingxi.workbench.admin.mcp.service.McpConnectionClient;
import com.lingxi.workbench.infra.ai.mcp.McpClientSecurityException;
import com.lingxi.workbench.infra.ai.mcp.McpSyncClientFactory;
import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.CONNECTION_ERROR_CODE;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.INVALID_ADDRESS_ERROR_CODE;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.MAX_DISCOVERED_TOOL_COUNT;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.MAX_DISCOVERY_PAGE_COUNT;
import static com.lingxi.workbench.admin.mcp.common.constants.McpServerConstants.TIMEOUT_ERROR_CODE;

/**
 * 使用 Spring AI 2.0 对应 MCP Java SDK 的 Streamable HTTP 客户端。
 *
 * <p>管理端配置是动态数据，不能依赖 application.yml 静态自动配置客户端。
 * 每次测试或发现工具时创建短生命周期客户端，完成后立即关闭连接。</p>
 */
@Component
@RequiredArgsConstructor
public class SpringAiMcpConnectionClient implements McpConnectionClient {

    private final McpSyncClientFactory clientFactory;

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
        try (McpSyncClient client = clientFactory.openInitialized(connectionConfig)) {
            McpSchema.InitializeResult initialization = client.getCurrentInitializationResult();
            List<McpDiscoveredTool> tools = discoverTools(client);
            String serverName = initialization.serverInfo().name();
            String serverVersion = initialization.serverInfo().version();
            return McpInspectionResult.builder()
                    .status(McpConnectionTestStatus.SUCCESS)
                    .latencyMs(elapsedMillis(startedAt))
                    .message("连接成功")
                    .serverName(serverName)
                    .serverVersion(serverVersion)
                    .protocolVersion(initialization.protocolVersion())
                    .tools(tools)
                    .build();
        }
    }

    private static List<McpDiscoveredTool> discoverTools(McpSyncClient client) {
        List<McpDiscoveredTool> tools = new ArrayList<>();
        String cursor = null;
        int pageCount = 0;
        do {
            McpSchema.ListToolsResult page;
            if (Objects.nonNull(cursor) && !cursor.isBlank()) {
                // 游标非空时按游标继续分页
                page = client.listTools(cursor);
            } else {
                // 首次调用获取第一页
                page = client.listTools();
            }
            page.tools().stream().map(SpringAiMcpConnectionClient::toDiscoveredTool).forEach(tools::add);
            if (tools.size() > MAX_DISCOVERED_TOOL_COUNT) {
                throw new IllegalStateException("MCP Server 工具数量超过平台发现上限");
            }
            cursor = page.nextCursor();
            pageCount++;
        } while (Objects.nonNull(cursor) && !cursor.isBlank() && pageCount < MAX_DISCOVERY_PAGE_COUNT);
        if (Objects.nonNull(cursor) && !cursor.isBlank()) {
            throw new IllegalStateException("MCP Server 工具分页数量超过平台发现上限");
        }
        return List.copyOf(tools);
    }

    private static McpDiscoveredTool toDiscoveredTool(McpSchema.Tool tool) {
        return McpDiscoveredTool.builder()
                .name(tool.name())
                .title(tool.title())
                .description(tool.description())
                .inputSchema(new JSONObject(tool.inputSchema()))
                .build();
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
