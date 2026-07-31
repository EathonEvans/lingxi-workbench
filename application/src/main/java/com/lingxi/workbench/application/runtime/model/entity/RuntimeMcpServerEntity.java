package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.application.runtime.common.enums.RuntimeMcpAuthenticationType;
import lombok.Data;

/**
 * Application 运行端只读 MCP Server 连接配置。
 */
@Data
@TableName("lingxi_core.ai_mcp_server")
public class RuntimeMcpServerEntity {

    private Long id;

    private String name;

    private String code;

    private String transportType;

    private String baseUrl;

    private String endpoint;

    private RuntimeMcpAuthenticationType authenticationType;

    private String secretCiphertext;

    private String secretIv;

    private Integer requestTimeoutSeconds;

    private String status;

    private String lastTestStatus;
}
