package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/** 执行一次已完成初始规划的Application请求。 */
@Getter
@Builder
public class PlanExecutionRequest {

    private String runId;
    private PlannerDecision initialDecision;
    private ConversationContext conversationContext;
    private ChatExecutionContext executionContext;
    private Set<String> confirmedCapabilityCodes;
    private SupervisorRuntimeContext supervisor;

    public Set<String> confirmedCapabilityCodes() {
        return confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }
}
