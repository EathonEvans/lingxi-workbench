package com.lingxi.workbench.admin.mcp.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.mcp.common.enums.McpAuthenticationType;
import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import com.lingxi.workbench.admin.mcp.common.enums.McpTransportType;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

/**
 * MCP Server 控制面连接配置。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_mcp_server")
public class McpServerEntity extends TimeEntity {

    private Long id;

    private String name;

    private String code;

    private String description;

    private McpTransportType transportType;

    private String baseUrl;

    private String endpoint;

    private McpAuthenticationType authenticationType;

    private String secretCiphertext;

    private String secretIv;

    private String secretHint;

    private Integer requestTimeoutSeconds;

    private EnableStatus status;

    private McpConnectionTestStatus lastTestStatus;

    private Integer lastTestLatencyMs;

    private String lastTestErrorCode;

    private String lastTestMessage;

    private OffsetDateTime lastTestAt;

    private Long createdBy;

    private Long updatedBy;
}
