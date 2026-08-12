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

    private final AgentToolRuntimeConfig runtimeConfig;
    private final TokenUsageAccumulator tokenUsage = new TokenUsageAccumulator();
    private final AgentToolExecutionLedger toolLedger = new AgentToolExecutionLedger();
    private int completedModelCalls;
    private int completedToolCalls;
    private boolean finalResponseRequested;

    AgentLoopState(AgentToolRuntimeConfig runtimeConfig) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "工具运行配置不能为空");
    }

    /**
     * 工作模型调用前校验软边界并占用一次调用额度。
     */
    void beginModelCall() {
        if (completedModelCalls >= runtimeConfig.getMaxModelCalls() - 1) {
            throw new AgentLoopLimitException("AGENT_MODEL_CALL_LIMIT", "处理步骤已达到上限，请缩小问题范围后重试",
                    "Agent工作模型额度已用尽，已保留最终回答调用，limit=" + runtimeConfig.getMaxModelCalls());
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
        if (hasReached(currentUsage.totalTokens(), runtimeConfig.getMaxTotalTokens())) {
            throw new AgentLoopLimitException("AGENT_TOTAL_TOKEN_LIMIT", "本次处理的信息量已达到上限，正在整理已有结果",
                    "Agent累计Token达到工作软上限，limit=" + runtimeConfig.getMaxTotalTokens());
        }
        runtimeConfig.getExecutionBudget().reserveModelCall();
        completedModelCalls++;
    }

    /**
     * 禁用工具的最终回答调用只校验硬边界，并使用Application预留额度。
     */
    void beginFinalModelCall() {
        if (completedModelCalls >= runtimeConfig.getMaxModelCalls()) {
            throw new AgentLoopLimitException("AGENT_MODEL_CALL_LIMIT", "本次处理已达到模型调用硬上限",
                    "Agent最终回答没有可用模型调用额度，limit=" + runtimeConfig.getMaxModelCalls());
        }
        TokenUsage currentUsage = tokenUsage.snapshot();
        if (hasReached(currentUsage.totalTokens(), runtimeConfig.getMaxFinalizationTokens())) {
            throw new AgentLoopLimitException("AGENT_TOTAL_TOKEN_HARD_LIMIT", "本次处理已达到Token硬上限",
                    "Agent累计Token达到最终回答硬上限，limit=" + runtimeConfig.getMaxFinalizationTokens());
        }
        runtimeConfig.getExecutionBudget().reserveFinalModelCall();
        completedModelCalls++;
    }

    /**
     * 记录一轮模型调用的真实 Token 用量。
     */
    void recordUsage(TokenUsage usage) {
        tokenUsage.add(usage);
    }

    /**
     * 尝试占用模型本轮请求的工具数量；额度不足时由调用方直接进入最终回答。
     */
    boolean tryRecordToolCalls(int requestedToolCalls) {
        if (completedToolCalls + requestedToolCalls > runtimeConfig.getMaxToolCalls()) {
            return false;
        }
        runtimeConfig.getExecutionBudget().reserveToolCalls(requestedToolCalls);
        completedToolCalls += requestedToolCalls;
        return true;
    }

    AgentToolExecutionLedger toolLedger() {
        return toolLedger;
    }

    /**
     * 重复调用出现后只允许再进行一次禁用工具的总结调用。
     */
    void requestFinalResponse() {
        finalResponseRequested = true;
    }

    /**
     * 判断下一轮是否必须禁用工具并收敛为最终回答。
     */
    boolean shouldFinalize() {
        return finalResponseRequested || completedModelCalls >= runtimeConfig.getMaxModelCalls() - 1
                || hasReached(tokenUsage.snapshot().totalTokens(), runtimeConfig.getMaxTotalTokens());
    }

    /**
     * Token软上限命中后不再执行模型刚请求的新工具，避免继续扩大上下文。
     */
    boolean shouldFinalizeBeforeToolExecution() {
        return hasReached(tokenUsage.snapshot().totalTokens(), runtimeConfig.getMaxTotalTokens());
    }

    private static boolean hasReached(Integer usedTokens, int limit) {
        return limit > 0 && Objects.nonNull(usedTokens) && usedTokens >= limit;
    }
}
