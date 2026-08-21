package com.lingxi.workbench.agentruntime.plan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

/** Runtime 提交给 Application 能力适配器的可信调用。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeCapabilityInvocation {

    private final RuntimeCapability capability;
    private final Map<String, String> inputs;
    private final AgentInvocationState invocationState;
    private final Set<String> confirmedCapabilityCodes;

    public Map<String, String> getInputs() {
        return Map.copyOf(inputs == null ? Map.of() : inputs);
    }

    public Set<String> getConfirmedCapabilityCodes() {
        return Set.copyOf(confirmedCapabilityCodes == null ? Set.of() : confirmedCapabilityCodes);
    }
}
