package com.lingxi.workbench.infra.ai.tool.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/** Tool 执行前提交给 HITL 门禁的可信请求。 */
@Getter
@Builder
public class AgentToolApprovalRequest {

    private final AgentToolDefinition definition;
    private final AgentToolExecutionContext executionContext;
    private final String toolInput;

    public AgentToolApprovalRequest(AgentToolDefinition definition, AgentToolExecutionContext executionContext,
                                    String toolInput) {
        this.definition = Objects.requireNonNull(definition, "工具定义不能为空");
        this.executionContext = Objects.requireNonNull(executionContext, "工具执行上下文不能为空");
        this.toolInput = Objects.requireNonNull(toolInput, "工具参数不能为空");
    }
}
