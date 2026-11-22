package com.lingxi.workbench.agentruntime.plan;

import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/** Supervisor 在统一 AgentRunRequest 中附加的计划与受控能力配置。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SupervisorRuntimeConfig {

    private final PlanPolicy planPolicy;
    private final int maxDelegations;
    private final List<RuntimeCapability> capabilities;
    private final Set<String> confirmedCapabilityCodes;
    private final RuntimeCapabilityInvoker capabilityInvoker;
    private final RuntimeTaskStore taskStore;

    public List<RuntimeCapability> getCapabilities() {
        return List.copyOf(capabilities == null ? List.of() : capabilities);
    }

    public Set<String> getConfirmedCapabilityCodes() {
        return Set.copyOf(confirmedCapabilityCodes == null ? Set.of() : confirmedCapabilityCodes);
    }
}
