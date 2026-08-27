package com.lingxi.workbench.admin.tool.model.bo;

import com.lingxi.workbench.framework.json.JSONObject;
import lombok.Builder;
import lombok.Getter;

/**
 * 已由服务端重新发现并校验的 MCP Tool 导入数据。
 */
@Getter
@Builder
public class McpToolImport {

    private final Long mcpServerId;

    private final String remoteToolName;

    private final String code;

    private final String name;

    private final String description;

    private final JSONObject parameterSchema;
}
