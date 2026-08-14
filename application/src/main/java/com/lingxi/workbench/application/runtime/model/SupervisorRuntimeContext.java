package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 单次 Supervisor 运行锁定的能力、AgentVersion 与可选路由证据。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SupervisorRuntimeContext {
    private final ApplicationCapabilityDefinition capability;
    private final AgentRuntimeSnapshot agent;
    private final IntentRoutingEvidence routingEvidence;
}
