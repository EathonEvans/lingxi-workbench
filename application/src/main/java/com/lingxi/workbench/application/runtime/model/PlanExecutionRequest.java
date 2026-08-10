package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;

/** 执行一次已完成初始规划的Application请求。 */
@Getter
@Builder
public class PlanExecutionRequest {

    private String runId;
    private ExecutionDecision initialDecision;
    private ConversationContext conversationContext;
    private ChatExecutionContext executionContext;
    private Set<String> confirmedCapabilityCodes;
    private Instant startedAt;
    private Integer initialPlannerModelCalls;

    public Set<String> confirmedCapabilityCodes() {
        return confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }
}
