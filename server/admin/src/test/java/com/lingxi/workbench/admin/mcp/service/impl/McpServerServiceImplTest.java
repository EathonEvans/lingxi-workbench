package com.lingxi.workbench.admin.mcp.service.impl;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.mcp.common.enums.McpAuthenticationType;
import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import com.lingxi.workbench.admin.mcp.common.enums.McpTransportType;
import com.lingxi.workbench.admin.mcp.model.bo.McpDiscoveredTool;
import com.lingxi.workbench.admin.mcp.model.bo.McpInspectionResult;
import com.lingxi.workbench.admin.mcp.model.converter.McpServerConverter;
import com.lingxi.workbench.admin.mcp.model.entity.McpServerEntity;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerInspectionVO;
import com.lingxi.workbench.admin.mcp.repo.McpServerRepo;
import com.lingxi.workbench.admin.mcp.service.McpConnectionClient;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.security.SecretCipher;
import com.lingxi.workbench.infra.ai.mcp.McpServerAddressPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerServiceImplTest {

    @Mock private McpServerRepo mcpServerRepo;
    @Mock private McpServerConverter mcpServerConverter;
    @Mock private McpConnectionClient mcpConnectionClient;
    @Mock private McpServerAddressPolicy addressPolicy;
    @Mock private SecretCipher secretCipher;
    @Mock private AuditService auditService;

    @InjectMocks private McpServerServiceImpl service;

    @Test
    void shouldPersistSuccessfulInspectionAndReturnDiscoveredTools() {
        McpServerEntity entity = server();
        McpDiscoveredTool tool = McpDiscoveredTool.builder()
                .name("get_order")
                .description("查询订单")
                .inputSchema(JSONObject.of("type", "object"))
                .build();
        McpInspectionResult result = McpInspectionResult.builder()
                .status(McpConnectionTestStatus.SUCCESS)
                .latencyMs(46)
                .message("连接成功")
                .serverName("order-server")
                .serverVersion("1.0.0")
                .protocolVersion("2025-11-25")
                .tools(List.of(tool))
                .build();
        McpServerInspectionVO expected = McpServerInspectionVO.builder().status(McpConnectionTestStatus.SUCCESS).build();

        when(mcpServerRepo.findById(10L)).thenReturn(Optional.of(entity));
        when(mcpConnectionClient.inspect(any())).thenReturn(result);
        when(mcpServerConverter.toInspectionVO(result)).thenReturn(expected);

        McpServerInspectionVO actual = service.discoverTools(10L, 7L);

        assertEquals(expected, actual);
        assertEquals(McpConnectionTestStatus.SUCCESS, entity.getLastTestStatus());
        assertEquals(46, entity.getLastTestLatencyMs());
        assertEquals("连接成功", entity.getLastTestMessage());
        assertNotNull(entity.getLastTestAt());
        verify(mcpServerRepo).save(entity);
        verify(auditService).record("MCP", "SERVER", 10L, 7L, "MCP_SERVER_CONNECTION_TEST", "测试状态=SUCCESS, 耗时=46ms");
    }

    @Test
    void shouldRejectEnableWhenConnectionWasNotSuccessful() {
        McpServerEntity entity = server();
        entity.setLastTestStatus(McpConnectionTestStatus.UNTESTED);
        when(mcpServerRepo.findById(10L)).thenReturn(Optional.of(entity));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.changeStatus(10L, EnableStatus.ACTIVE, 7L));

        assertEquals("MCP Server 最近一次连接测试未成功，不能启用", exception.getMessage());
    }

    private static McpServerEntity server() {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(10L);
        entity.setName("订单服务");
        entity.setCode("order_server");
        entity.setTransportType(McpTransportType.STREAMABLE_HTTP);
        entity.setBaseUrl("https://mcp.example.com");
        entity.setEndpoint("/mcp");
        entity.setAuthenticationType(McpAuthenticationType.NONE);
        entity.setRequestTimeoutSeconds(20);
        entity.setStatus(EnableStatus.DISABLED);
        entity.setLastTestStatus(McpConnectionTestStatus.UNTESTED);
        return entity;
    }
}
