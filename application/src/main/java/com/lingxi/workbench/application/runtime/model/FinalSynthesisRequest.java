package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/** 使用锁定的主Agent版本对已完成步骤结果进行最终综合。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FinalSynthesisRequest {
    private final ConversationContext conversationContext;
    private final ChatExecutionContext executionContext;
    private final List<PlanStepResult> stepResults;
    private final String limitation;
    private final SupervisorRuntimeContext supervisor;

    public List<PlanStepResult> stepResults() {
        return List.copyOf(Objects.requireNonNullElse(stepResults, List.of()));
    }

    public boolean isLimited() {
        return limitation != null && !limitation.isBlank();
    }
}
