package com.lingxi.workbench.agentruntime.plan;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/** 能力适配器返回给 Runtime 的结果与实际预算消耗。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeCapabilityResult {

    private final String content;
    private final TokenUsage usage;
    private final int modelCalls;
    private final int toolCalls;
    private final long lastSequenceNo;
    private final RuntimeInterruption interruption;

    public TokenUsage getUsage() {
        return Objects.requireNonNullElseGet(usage, TokenUsage::empty);
    }

    public boolean isInterrupted() {
        return interruption != null;
    }
}
