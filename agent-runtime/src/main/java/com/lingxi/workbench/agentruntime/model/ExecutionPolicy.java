package com.lingxi.workbench.agentruntime.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.util.Objects;

/** 一次 Agent 调用不可突破的模型、Tool、Token、硬截止和活跃输出宽限。 */
@Getter
public final class ExecutionPolicy {

    private final int maxModelCalls;
    private final int maxToolCalls;
    private final int maxTotalTokens;
    private final int maxFinalizationTokens;
    private final Duration timeout;
    private final Duration activeOutputGrace;

    @Builder
    private ExecutionPolicy(int maxModelCalls, int maxToolCalls, int maxTotalTokens,
                            int maxFinalizationTokens, Duration timeout, Duration activeOutputGrace) {
        if (maxModelCalls < 1 || maxToolCalls < 0 || maxTotalTokens < 1
                || maxFinalizationTokens < maxTotalTokens || Objects.isNull(timeout)
                || timeout.isZero() || timeout.isNegative() || Objects.isNull(activeOutputGrace)
                || activeOutputGrace.isNegative()) {
            throw new IllegalArgumentException("Agent执行策略的模型、Tool、Token和超时边界不合法");
        }
        this.maxModelCalls = maxModelCalls;
        this.maxToolCalls = maxToolCalls;
        this.maxTotalTokens = maxTotalTokens;
        this.maxFinalizationTokens = maxFinalizationTokens;
        this.timeout = timeout;
        this.activeOutputGrace = activeOutputGrace;
    }
}
