package com.lingxi.workbench.agentruntime.plan;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.planner.PlannerClarification;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/** Supervisor 从规划到最终回答的 Runtime 结果。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SupervisorRunResult {

    private final SupervisorRunOutcome outcome;
    private final String content;
    private final PlannerClarification clarification;
    private final RuntimeInterruption interruption;
    private final TokenUsage runtimeUsage;
    private final long lastSequenceNo;

    public TokenUsage getRuntimeUsage() {
        return Objects.requireNonNullElseGet(runtimeUsage, TokenUsage::empty);
    }
}
