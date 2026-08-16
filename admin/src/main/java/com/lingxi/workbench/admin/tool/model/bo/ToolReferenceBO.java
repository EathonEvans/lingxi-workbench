package com.lingxi.workbench.admin.tool.model.bo;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.tool.common.enums.ToolSourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import com.lingxi.workbench.infra.ai.tool.common.enums.ToolRiskLevel;

/** 供 Application 发布时冻结的 Tool 只读契约。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ToolReferenceBO {

    private final Long id;
    private final String name;
    private final String code;
    private final String description;
    private final ToolSourceType sourceType;
    private final Long mcpServerId;
    private final String remoteToolName;
    private final JSONObject parameterSchema;
    private final ToolRiskLevel riskLevel;
    private final Boolean approvalRequired;
    private final Integer approvalTtlSeconds;
    private final String resourceScope;
    private final EnableStatus status;
}
