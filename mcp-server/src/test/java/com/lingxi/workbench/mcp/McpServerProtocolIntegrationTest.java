package com.lingxi.workbench.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 HTTP Transport 验证本地 MCP Server 的协议闭环。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerProtocolIntegrationTest {

    @Value("${local.server.port}")
    private int serverPort;

    @Test
    void shouldInitializeDiscoverToolsAndCallCalculator() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + serverPort)
                .endpoint("/mcp")
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .initializationTimeout(Duration.ofSeconds(5))
                .clientInfo(McpSchema.Implementation.builder("lingxi-mcp-integration-test", "1.0.0").build())
                .build()) {
            McpSchema.InitializeResult initialization = client.initialize();
            McpSchema.ListToolsResult toolList = client.listTools();
            Set<String> toolNames = toolList.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());

            assertEquals("lingxi-local-mcp-server", initialization.serverInfo().name());
            assertTrue(toolNames.contains("system_calculator"));
            assertTrue(toolNames.contains("system_current_time"));
            McpSchema.Tool calculator = toolList.tools().stream().filter(tool -> "system_calculator".equals(tool.name())).findFirst().orElseThrow();
            assertTrue(calculator.inputSchema().toString().contains("leftOperand"));
            assertTrue(calculator.inputSchema().toString().contains("operator"));
            assertTrue(calculator.inputSchema().toString().contains("rightOperand"));

            Map<String, Object> arguments = Map.of("leftOperand", 2, "operator", "ADD", "rightOperand", 3);
            McpSchema.CallToolResult callResult = client.callTool(McpSchema.CallToolRequest.builder("system_calculator").arguments(arguments).build());
            McpSchema.TextContent content = (McpSchema.TextContent) callResult.content().getFirst();

            assertFalse(Boolean.TRUE.equals(callResult.isError()));
            assertTrue(content.text().contains("\"result\":\"5\""));

            Map<String, Object> invalidArguments = Map.of("leftOperand", 1, "operator", "DIVIDE", "rightOperand", 0);
            McpSchema.CallToolResult errorResult = client.callTool(McpSchema.CallToolRequest.builder("system_calculator").arguments(invalidArguments).build());
            McpSchema.TextContent errorContent = (McpSchema.TextContent) errorResult.content().getFirst();

            assertTrue(Boolean.TRUE.equals(errorResult.isError()));
            assertTrue(errorContent.text().contains("除数不能为0"));
        }
    }
}
