package com.lingxi.workbench.admin.mcp.model.bo;

import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * MCP 初始化与工具发现的完整结果。
 */
@Getter
@Builder
public class McpInspectionResult {

    private final McpConnectionTestStatus status;

    private final int latencyMs;

    private final String errorCode;

    private final String message;

    private final String serverName;

    private final String serverVersion;

    private final String protocolVersion;

    @Builder.Default
    private final List<McpDiscoveredTool> tools = List.of();

    public static McpInspectionResult failed(int latencyMs, String errorCode, String message) {
        return McpInspectionResult.builder()
                .status(McpConnectionTestStatus.FAILED)
                .latencyMs(latencyMs)
                .errorCode(errorCode)
                .message(message)
                .build();
    }

    public boolean isSuccess() {
        return status == McpConnectionTestStatus.SUCCESS;
    }
}
