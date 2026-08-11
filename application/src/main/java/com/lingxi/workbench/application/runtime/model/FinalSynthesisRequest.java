package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.ccontext.ConversationContext;

import java.util.List;

/**
 * @param conversationContext 路由前准备的共享上下文
 * @param executionContext    本轮共享执行状态与剩余预算
 * @param stepResults         已完成计划步骤的受限结果
 * @param limitation          触发提前收口的安全原因；正常综合时为空
 */
public record FinalSynthesisRequest(ConversationContext conversationContext, ChatExecutionContext executionContext,
                                    List<PlanStepResult> stepResults, String limitation) {

    public FinalSynthesisRequest(ConversationContext conversationContext, ChatExecutionContext executionContext,
                                 List<PlanStepResult> stepResults) {
        this(conversationContext, executionContext, stepResults, null);
    }

    public FinalSynthesisRequest {
        stepResults = java.util.Optional.ofNullable(stepResults).map(List::copyOf).orElseGet(List::of);
    }

    public boolean isLimited() {
        return limitation != null && !limitation.isBlank();
    }
}
