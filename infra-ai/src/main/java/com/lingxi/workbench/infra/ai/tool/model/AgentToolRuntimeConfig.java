package com.lingxi.workbench.infra.ai.tool.model;

import com.lingxi.workbench.infra.ai.tool.AgentToolEventPublisher;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * 单次模型回答使用的工具运行配置。
 *
 * <p>工具实例与调用边界只存在于本次请求，不能写入不可变 AgentVersion JSON 快照。</p>
 */
@Getter
public class AgentToolRuntimeConfig {

    /**
     * 本次允许暴露给模型的工具。
     */
    private final List<AgentTool> tools;
    /**
     * 服务端可信运行上下文。
     */
    private final AgentToolExecutionContext executionContext;
    /**
     * 单次 Agent 节点最多执行的工具次数。
     */
    private final int maxToolCalls;
    /**
     * 回答阶段最多调用模型的次数，包含工具结果回传后的继续推理。
     */
    private final int maxModelCalls;
    /**
     * Agent 多轮模型调用累计输入 Token 上限；小于等于零表示不在此层限制。
     */
    private final int maxInputTokens;
    /**
     * Agent 多轮模型调用累计输出 Token 上限；小于等于零表示不在此层限制。
     */
    private final int maxOutputTokens;
    /**
     * Agent正常探索阶段累计输入与输出Token软上限；小于等于零表示不在此层限制。
     */
    private final int maxTotalTokens;
    /**
     * 禁用工具后的最终回答累计Token硬上限；小于等于零表示不在此层限制。
     */
    private final int maxFinalizationTokens;
    /**
     * 单次工具结果允许回传模型的最大字符数。
     */
    private final int maxResultCharacters;
    /**
     * 工具执行状态发布器；未提供时使用空实现，不影响工具主流程。
     */
    private final AgentToolEventPublisher eventPublisher;
    /** 跨Planner、Workflow和成员Agent共享的Application调用预算。 */
    private final AgentExecutionBudget executionBudget;

    @Builder
    private AgentToolRuntimeConfig(List<AgentTool> tools, AgentToolExecutionContext executionContext, int maxToolCalls, int maxModelCalls, int maxInputTokens, int maxOutputTokens, int maxTotalTokens, int maxFinalizationTokens, int maxResultCharacters, AgentToolEventPublisher eventPublisher, AgentExecutionBudget executionBudget) {
        this.tools = List.copyOf(Objects.requireNonNull(tools, "运行时工具列表不能为空"));
        this.executionContext = Objects.requireNonNull(executionContext, "工具执行上下文不能为空");
        this.maxToolCalls = maxToolCalls;
        this.maxModelCalls = maxModelCalls;
        this.maxInputTokens = maxInputTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.maxTotalTokens = maxTotalTokens;
        this.maxFinalizationTokens = maxFinalizationTokens;
        this.maxResultCharacters = maxResultCharacters;
        this.eventPublisher = eventPublisher;
        this.executionBudget = Objects.requireNonNullElseGet(executionBudget, AgentExecutionBudget::noop);
    }

    /**
     * 是否启用了至少一个工具。
     */
    public boolean enabled() {
        return !tools.isEmpty();
    }

    /**
     * 返回始终可调用的工具事件发布器。
     */
    public AgentToolEventPublisher resolveEventPublisher() {
        return Objects.requireNonNullElseGet(eventPublisher, AgentToolEventPublisher::noop);
    }
}
