package com.lingxi.workbench.admin.mcp.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.mcp.common.enums.McpAuthenticationType;
import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import com.lingxi.workbench.admin.mcp.common.enums.McpTransportType;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * MCP Server 管理详情，不返回密钥明文和密文。
 */
@Data
@Builder
public class McpServerVO {

    private String id;

    private String name;

    private String code;

    private String description;

    private McpTransportType transportType;

    private String baseUrl;

    private String endpoint;

    private McpAuthenticationType authenticationType;

    private String secretHint;

    private Integer requestTimeoutSeconds;

    private EnableStatus status;

    private McpConnectionTestStatus lastTestStatus;

    private Integer lastTestLatencyMs;

    private String lastTestErrorCode;

    private String lastTestMessage;

    private OffsetDateTime lastTestAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
