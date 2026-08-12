package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/** 已解析锁定 AgentVersion 后交给具体 Agent 执行器的可信请求。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentExecutionRequest {
    private final ApplicationCapabilityDefinition capability;
    private final AgentRuntimeSnapshot agent;
    private final String query;
    private final ChatExecutionContext executionContext;
    private final IntentRoutingEvidence routingEvidence;
    private final Set<String> confirmedCapabilityCodes;

    public Set<String> confirmedCapabilityCodes() {
        return confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }
}
