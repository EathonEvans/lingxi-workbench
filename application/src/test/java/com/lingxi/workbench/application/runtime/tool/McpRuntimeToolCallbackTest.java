package com.lingxi.workbench.application.runtime.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.RuntimeMcpAuthenticationType;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeMcpServerEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.mcp.AgentScopeMcpClientFactory;
import com.lingxi.workbench.infra.ai.mcp.model.McpClientConnectionConfig;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.model.ToolContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpRuntimeToolCallbackTest {

    @Mock private AgentScopeMcpClientFactory clientFactory;
    @Mock private McpClientWrapper client;
    @Mock private SecretCipher secretCipher;

    @Test
    void shouldInvokeRemoteMcpToolThroughAgentScopeClient() {
        RuntimeToolEntity tool = new RuntimeToolEntity();
        tool.setCode("get_order");
        tool.setDescription("查询订单");
        tool.setRemoteToolName("remote_get_order");
        tool.setParameterSchema(JSONObject.of("type", "object"));
        RuntimeMcpServerEntity server = new RuntimeMcpServerEntity();
        server.setCode("order_mcp");
        server.setBaseUrl("https://mcp.example.com");
        server.setEndpoint("/mcp");
        server.setAuthenticationType(RuntimeMcpAuthenticationType.NONE);
        server.setRequestTimeoutSeconds(20);
        McpSchema.CallToolResult remoteResult = new McpSchema.CallToolResult("订单已找到", false);
        when(clientFactory.openInitialized(eq("lingxi-order_mcp"), any(McpClientConnectionConfig.class)))
                .thenReturn(client);
        when(client.callTool(eq("remote_get_order"), any())).thenReturn(Mono.just(remoteResult));

        String result = new McpRuntimeToolCallback(tool, server, clientFactory, secretCipher)
                .call("{\"orderId\":\"A-1\"}");

        assertEquals(JSON.toJSONString(remoteResult.content()), result);
        verify(client).callTool(eq("remote_get_order"), org.mockito.ArgumentMatchers.argThat(arguments -> {
            assertEquals("A-1", arguments.get("orderId"));
            return true;
        }));
        verify(client).close();
    }

    @Test
    void shouldPassToolContextAsMcpMetaWithoutWebExchange() {
        RuntimeToolEntity tool = new RuntimeToolEntity();
        tool.setCode("get_order");
        tool.setDescription("查询订单");
        tool.setRemoteToolName("remote_get_order");
        tool.setParameterSchema(JSONObject.of("type", "object"));
        RuntimeMcpServerEntity server = new RuntimeMcpServerEntity();
        server.setCode("order_mcp");
        server.setBaseUrl("https://mcp.example.com");
        server.setEndpoint("/mcp");
        server.setAuthenticationType(RuntimeMcpAuthenticationType.NONE);
        server.setRequestTimeoutSeconds(20);
        McpSchema.CallToolResult remoteResult = new McpSchema.CallToolResult("订单已找到", false);
        when(clientFactory.openInitialized(eq("lingxi-order_mcp"), any(McpClientConnectionConfig.class)))
                .thenReturn(client);
        when(client.callTool(eq("remote_get_order"), any(), any())).thenReturn(Mono.just(remoteResult));

        new McpRuntimeToolCallback(tool, server, clientFactory, secretCipher).call(
                "{\"orderId\":\"A-1\"}", new ToolContext(Map.of("requestId", "R-1", "exchange", "ignored")));

        verify(client).callTool(eq("remote_get_order"), any(), org.mockito.ArgumentMatchers.argThat(metadata -> {
            assertEquals("R-1", metadata.get("requestId"));
            assertFalse(metadata.containsKey("exchange"));
            return true;
        }));
        verify(client).close();
    }
}
