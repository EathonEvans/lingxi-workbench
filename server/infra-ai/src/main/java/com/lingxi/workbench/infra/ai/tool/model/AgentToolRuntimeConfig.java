package com.lingxi.workbench.infra.ai.tool.model;

import com.lingxi.workbench.infra.ai.tool.AgentTool;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/** 单次 Agent 调用已授权的 Tool 执行器与服务端可信上下文。 */
@Getter
public class AgentToolRuntimeConfig {

    private final List<AgentTool> tools;
    private final AgentToolExecutionContext executionContext;
    private final int maxResultCharacters;

    @Builder
    private AgentToolRuntimeConfig(List<AgentTool> tools, AgentToolExecutionContext executionContext,
                                   int maxResultCharacters) {
        this.tools = List.copyOf(Objects.requireNonNull(tools, "运行时工具列表不能为空"));
        this.executionContext = Objects.requireNonNull(executionContext, "工具执行上下文不能为空");
        if (maxResultCharacters < 1) {
            throw new IllegalArgumentException("Tool结果字符上限必须大于0");
        }
        this.maxResultCharacters = maxResultCharacters;
    }

    public boolean enabled() {
        return !tools.isEmpty();
    }
}
