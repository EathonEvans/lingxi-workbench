package com.lingxi.workbench.chat.model.ccontext;

import lombok.Builder;
import lombok.Getter;

/**
 * 单轮共享上下文准备阶段的资源上限。
 *
 * <p>该预算只约束路由和执行共同使用的基础上下文。Agent、Workflow 和后续 Planner
 * 仍需在各自模型边界执行更严格的输入输出预算。</p>
 */
@Getter
public final class ContextBudget {

    private final int maxHistoryRounds;
    private final int maxSystemPromptTokens;
    private final int maxCapabilityTokens;
    private final int maxHistoryTokens;
    private final int maxMemoryTokens;
    private final int maxInputTokens;
    private final int maxObservationTokens;
    private final int reservedOutputTokens;

    @Builder
    private ContextBudget(int maxHistoryRounds, int maxSystemPromptTokens, int maxCapabilityTokens, int maxHistoryTokens, int maxMemoryTokens, int maxInputTokens, int maxObservationTokens, int reservedOutputTokens) {
        if (maxHistoryRounds <= 0 || maxSystemPromptTokens <= 0 || maxCapabilityTokens <= 0 || maxHistoryTokens <= 0
                || maxMemoryTokens <= 0 || maxInputTokens <= 0 || maxObservationTokens <= 0 || reservedOutputTokens <= 0) {
            throw new IllegalArgumentException("共享上下文预算必须大于0");
        }
        this.maxHistoryRounds = maxHistoryRounds;
        this.maxSystemPromptTokens = maxSystemPromptTokens;
        this.maxCapabilityTokens = maxCapabilityTokens;
        this.maxHistoryTokens = maxHistoryTokens;
        this.maxMemoryTokens = maxMemoryTokens;
        this.maxInputTokens = maxInputTokens;
        this.maxObservationTokens = maxObservationTokens;
        this.reservedOutputTokens = reservedOutputTokens;
    }
}
