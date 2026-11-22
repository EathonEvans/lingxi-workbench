package com.lingxi.workbench.admin.mcp.service;

import com.lingxi.workbench.infra.ai.mcp.McpClientSecurityException;
import com.lingxi.workbench.infra.ai.mcp.McpServerAddressPolicy;
import com.lingxi.workbench.infra.ai.mcp.config.McpClientSecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpServerAddressPolicyTest {

    @Test
    void shouldAllowConfiguredLocalMcpServer() {
        McpClientSecurityProperties properties = new McpClientSecurityProperties();
        properties.setAllowedPrivateHosts(List.of("localhost"));
        McpServerAddressPolicy policy = new McpServerAddressPolicy(properties);

        assertDoesNotThrow(() -> policy.validateConfiguration("http://localhost:8080", "/mcp"));
        assertDoesNotThrow(() -> policy.validateOutboundAddress("http://localhost:8080"));
    }

    @Test
    void shouldRejectPrivateAddressWithoutAllowlist() {
        McpServerAddressPolicy policy = new McpServerAddressPolicy(new McpClientSecurityProperties());

        McpClientSecurityException exception = assertThrows(McpClientSecurityException.class, () -> policy.validateOutboundAddress("http://127.0.0.1:8080"));

        assertEquals("MCP Server 指向私网地址，请先加入服务端白名单", exception.getMessage());
    }

    @Test
    void shouldRejectBaseUrlContainingEndpointPath() {
        McpServerAddressPolicy policy = new McpServerAddressPolicy(new McpClientSecurityProperties());

        McpClientSecurityException exception = assertThrows(McpClientSecurityException.class, () -> policy.validateConfiguration("https://example.com/mcp", "/mcp"));

        assertEquals("MCP Server 地址只填写协议、主机和端口，接口路径请填写在 Endpoint", exception.getMessage());
    }
}
