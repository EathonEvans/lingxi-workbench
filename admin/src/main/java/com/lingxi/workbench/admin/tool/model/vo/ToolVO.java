package com.lingxi.workbench.admin.tool.model.vo;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.common.enums.ToolSourceType;
import com.lingxi.workbench.admin.tool.model.config.ToolParameterDefinition;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tool 管理详情；Long 主键转换为字符串，避免前端数值精度丢失。
 */
@Data
@Builder
public class ToolVO {

    private String id;

    private String name;

    private String code;

    private String description;

    private ToolSourceType sourceType;

    private String mcpServerId;

    private String remoteToolName;

    private List<ToolParameterDefinition> parameters;

    private EnableStatus status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
