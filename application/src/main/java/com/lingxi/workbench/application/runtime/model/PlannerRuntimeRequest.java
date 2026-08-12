package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/** 初始Planner模型调用和后续计划执行的聚合输入。 */
@Getter
@Builder
public class PlannerRuntimeRequest {

    private String runId;
    private ConversationContext conversationContext;
    private ChatExecutionContext executionContext;
    private Set<String> confirmedCapabilityCodes;
    private PlanExecutorRuntimeContext planExecutor;

    public Set<String> confirmedCapabilityCodes() {
        return confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }
}
