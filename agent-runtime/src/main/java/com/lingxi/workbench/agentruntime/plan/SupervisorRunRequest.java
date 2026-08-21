package com.lingxi.workbench.agentruntime.plan;

import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionValidationContext;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/** Application 完成版本、Prompt、能力和上下文准备后提交给 Supervisor Runtime 的可信请求。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SupervisorRunRequest {

    private final String runId;
    private final Long agentId;
    private final Long agentVersionId;
    private final String plannerSystemPrompt;
    private final String synthesisSystemPrompt;
    private final String query;
    private final List<ChatHistoryMessage> history;
    private final ChatModelRuntimeConfig modelConfig;
    private final PlannerDecisionValidationContext validationContext;
    private final PlanPolicy planPolicy;
    private final ExecutionPolicy executionPolicy;
    private final int maxDelegations;
    private final int maxObservationTokens;
    private final List<RuntimeCapability> capabilities;
    private final Set<String> confirmedCapabilityCodes;
    private final RuntimeCapabilityInvoker capabilityInvoker;
    private final RuntimePlanStore planStore;
    private final RuntimeEventSink eventSink;
    private final long initialSequenceNo;

    public List<ChatHistoryMessage> getHistory() {
        return List.copyOf(history == null ? List.of() : history);
    }

    public List<RuntimeCapability> getCapabilities() {
        return List.copyOf(capabilities == null ? List.of() : capabilities);
    }

    public Set<String> getConfirmedCapabilityCodes() {
        return Set.copyOf(confirmedCapabilityCodes == null ? Set.of() : confirmedCapabilityCodes);
    }
}
