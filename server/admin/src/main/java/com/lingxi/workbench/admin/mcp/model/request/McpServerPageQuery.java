package com.lingxi.workbench.admin.mcp.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.mcp.common.enums.McpConnectionTestStatus;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP Server 分页查询参数。
 */
@Getter
@Setter
public class McpServerPageQuery extends PageQuery {

    @Size(max = 64, message = "搜索关键字不能超过 64 个字符")
    private String keyword;

    private EnableStatus status;

    private McpConnectionTestStatus lastTestStatus;
}
