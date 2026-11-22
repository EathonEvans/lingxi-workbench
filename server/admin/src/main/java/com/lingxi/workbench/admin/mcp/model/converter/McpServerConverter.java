package com.lingxi.workbench.admin.mcp.model.converter;

import com.lingxi.workbench.admin.mcp.model.bo.McpDiscoveredTool;
import com.lingxi.workbench.admin.mcp.model.bo.McpInspectionResult;
import com.lingxi.workbench.admin.mcp.model.entity.McpServerEntity;
import com.lingxi.workbench.admin.mcp.model.vo.McpDiscoveredToolVO;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerInspectionVO;
import com.lingxi.workbench.admin.mcp.model.vo.McpServerVO;
import org.springframework.stereotype.Component;

/**
 * MCP Server 持久化模型、连接结果和接口模型转换器。
 */
@Component
public class McpServerConverter {

    public McpServerVO toVO(McpServerEntity entity) {
        return McpServerVO.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .code(entity.getCode())
                .description(entity.getDescription())
                .transportType(entity.getTransportType())
                .baseUrl(entity.getBaseUrl())
                .endpoint(entity.getEndpoint())
                .authenticationType(entity.getAuthenticationType())
                .secretHint(entity.getSecretHint())
                .requestTimeoutSeconds(entity.getRequestTimeoutSeconds())
                .status(entity.getStatus())
                .lastTestStatus(entity.getLastTestStatus())
                .lastTestLatencyMs(entity.getLastTestLatencyMs())
                .lastTestErrorCode(entity.getLastTestErrorCode())
                .lastTestMessage(entity.getLastTestMessage())
                .lastTestAt(entity.getLastTestAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public McpServerInspectionVO toInspectionVO(McpInspectionResult result) {
        return McpServerInspectionVO.builder()
                .status(result.getStatus())
                .latencyMs(result.getLatencyMs())
                .errorCode(result.getErrorCode())
                .message(result.getMessage())
                .serverName(result.getServerName())
                .serverVersion(result.getServerVersion())
                .protocolVersion(result.getProtocolVersion())
                .tools(result.getTools().stream().map(this::toToolVO).toList())
                .build();
    }

    private McpDiscoveredToolVO toToolVO(McpDiscoveredTool tool) {
        return McpDiscoveredToolVO.builder()
                .name(tool.getName())
                .title(tool.getTitle())
                .description(tool.getDescription())
                .inputSchema(tool.getInputSchema())
                .build();
    }
}
