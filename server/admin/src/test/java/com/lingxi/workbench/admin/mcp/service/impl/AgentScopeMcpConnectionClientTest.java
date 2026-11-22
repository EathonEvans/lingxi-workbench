package com.lingxi.workbench.admin.mcp.service.impl;

import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import com.lingxi.workbench.admin.mcp.model.bo.McpConnectionConfig;
import com.lingxi.workbench.admin.mcp.model.bo.McpInspectionResult;
import com.lingxi.workbench.infra.ai.mcp.AgentScopeMcpClientFactory;
import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentScopeMcpConnectionClientTest {

    @Mock private AgentScopeMcpClientFactory clientFactory;
    @Mock private McpClientWrapper client;

    @Test
    void shouldDiscoverAgentScopeMcpToolsAndPreserveJsonSchema() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of("orderId", Map.of("type", "string")), List.of("orderId"),
                false, Map.of("order", Map.of("type", "object")), Map.of());
        McpSchema.Tool tool = new McpSchema.Tool(
                "get_order", "查询订单", "根据订单编号查询订单", schema, Map.of(), null, Map.of());
        when(clientFactory.openInitialized(eq("lingxi-admin-mcp-inspection"), any(McpClientConnectionConfig.class)))
                .thenReturn(client);
        when(client.listTools()).thenReturn(Mono.just(List.of(tool)));

        McpInspectionResult result = new AgentScopeMcpConnectionClient(clientFactory).inspect(
                McpConnectionConfig.builder()
                        .baseUrl("https://mcp.example.com")
                        .endpoint("/mcp")
                        .requestTimeoutSeconds(20)
                        .build());

        assertEquals(McpConnectionTestStatus.SUCCESS, result.getStatus());
        assertEquals(1, result.getTools().size());
        assertEquals("get_order", result.getTools().getFirst().getName());
        assertEquals("object", result.getTools().getFirst().getInputSchema().getString("type"));
        assertTrue(result.getTools().getFirst().getInputSchema().getJSONObject("properties").containsKey("orderId"));
        assertFalse(result.getTools().getFirst().getInputSchema().getBooleanValue("additionalProperties"));
        ArgumentCaptor<McpClientConnectionConfig> configCaptor = ArgumentCaptor.forClass(McpClientConnectionConfig.class);
        verify(clientFactory).openInitialized(eq("lingxi-admin-mcp-inspection"), configCaptor.capture());
        assertEquals("https://mcp.example.com", configCaptor.getValue().getBaseUrl());
        verify(client).close();
    }
}
