package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.ccontext.ConversationContext;

import java.util.List;

/**
 * @param conversationContext 路由前准备的共享上下文
 * @param stepResults        已完成计划步骤的受限结果
 */
public record FinalSynthesisRequest(ConversationContext conversationContext, List<PlanStepResult> stepResults) {

    public FinalSynthesisRequest {
        stepResults = stepResults == null ? List.of() : List.copyOf(stepResults);
    }
}
