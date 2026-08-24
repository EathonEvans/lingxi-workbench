package com.lingxi.workbench.agentruntime.model;

import lombok.Builder;
import lombok.Getter;

/** 一次 Agent 调用不可突破的模型、Tool与Token预算。 */
@Getter
public final class ExecutionPolicy {

    private final int maxModelCalls;
    private final int maxToolCalls;
    private final int maxTotalTokens;
    private final int maxFinalizationTokens;

    @Builder
    private ExecutionPolicy(int maxModelCalls, int maxToolCalls, int maxTotalTokens,
                            int maxFinalizationTokens) {
        if (maxModelCalls < 1 || maxToolCalls < 0 || maxTotalTokens < 1
                || maxFinalizationTokens < maxTotalTokens) {
            throw new IllegalArgumentException("Agent执行策略的模型、Tool和Token边界不合法");
        }
        this.maxModelCalls = maxModelCalls;
        this.maxToolCalls = maxToolCalls;
        this.maxTotalTokens = maxTotalTokens;
        this.maxFinalizationTokens = maxFinalizationTokens;
    }
}
