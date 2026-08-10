package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.infra.ai.tool.AgentLoopLimitException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionLedger;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;

import java.util.Objects;

/**
 * 单次 ReAct 回答的循环状态，统一维护调用次数、Token 用量和工具执行账本。
 */
final class AgentLoopState {

    private static final double WARNING_RATIO = 0.7D;

    private final AgentToolRuntimeConfig runtimeConfig;
    private final TokenUsageAccumulator tokenUsage = new TokenUsageAccumulator();
    private final AgentToolExecutionLedger toolLedger = new AgentToolExecutionLedger();
    private int completedModelCalls;
    private int completedToolCalls;
    private boolean finalResponseRequested;

    AgentLoopState(AgentToolRuntimeConfig runtimeConfig) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "工具运行配置不能为空");
    }

    /** 模型调用前校验硬边界并占用一次调用额度。 */
    void beginModelCall() {
        if (completedModelCalls >= runtimeConfig.getMaxModelCalls()) {
            throw new AgentLoopLimitException("AGENT_MODEL_CALL_LIMIT", "处理步骤已达到上限，请缩小问题范围后重试",
                    "工具调用循环超过模型调用次数限制，limit=" + runtimeConfig.getMaxModelCalls());
        }
        TokenUsage currentUsage = tokenUsage.snapshot();
        if (hasReached(currentUsage.inputTokens(), runtimeConfig.getMaxInputTokens())) {
            throw new AgentLoopLimitException("AGENT_INPUT_TOKEN_LIMIT", "本次处理的信息量已达到上限，请缩小问题范围后重试",
                    "Agent累计输入Token超过限制，limit=" + runtimeConfig.getMaxInputTokens());
        }
        if (hasReached(currentUsage.outputTokens(), runtimeConfig.getMaxOutputTokens())) {
            throw new AgentLoopLimitException("AGENT_OUTPUT_TOKEN_LIMIT", "本次回答已达到生成上限，请拆分问题后重试",
                    "Agent累计输出Token超过限制，limit=" + runtimeConfig.getMaxOutputTokens());
        }
        completedModelCalls++;
    }

    /** 记录一轮模型调用的真实 Token 用量。 */
    void recordUsage(TokenUsage usage) {
        tokenUsage.add(usage);
    }

    /** 校验并累计模型本轮请求的工具数量。 */
    void recordToolCalls(int requestedToolCalls) {
        if (completedToolCalls + requestedToolCalls > runtimeConfig.getMaxToolCalls()) {
            throw new AgentLoopLimitException("AGENT_TOOL_CALL_LIMIT", "工具调用次数已达到上限，现有信息不足以继续处理",
                    "模型请求的工具调用次数超过Agent限制，limit=" + runtimeConfig.getMaxToolCalls());
        }
        completedToolCalls += requestedToolCalls;
    }

    AgentToolExecutionLedger toolLedger() {
        return toolLedger;
    }

    /** 重复调用出现后只允许再进行一次禁用工具的总结调用。 */
    void requestFinalResponse() {
        finalResponseRequested = true;
    }

    /** 判断下一轮是否必须禁用工具并收敛为最终回答。 */
    boolean shouldFinalize() {
        return finalResponseRequested || completedModelCalls >= runtimeConfig.getMaxModelCalls() - 1
                || isWarningThresholdReached();
    }

    private boolean isWarningThresholdReached() {
        TokenUsage currentUsage = tokenUsage.snapshot();
        return hasReachedRatio(currentUsage.inputTokens(), runtimeConfig.getMaxInputTokens())
                || hasReachedRatio(currentUsage.outputTokens(), runtimeConfig.getMaxOutputTokens());
    }

    private static boolean hasReached(Integer usedTokens, int limit) {
        return limit > 0 && Objects.nonNull(usedTokens) && usedTokens >= limit;
    }

    private static boolean hasReachedRatio(Integer usedTokens, int limit) {
        return limit > 0 && Objects.nonNull(usedTokens) && (double) usedTokens / limit >= WARNING_RATIO;
    }
}
