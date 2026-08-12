package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.service.agent.AgentExecutor;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentExecutorRegistryTest {

    @Test
    void shouldRequireExactlyOneExecutorForEveryAgentType() {
        AgentExecutor conversational = new StubExecutor(EnumSet.of(AgentType.REACT, AgentType.SUPERVISOR));
        AgentExecutor planExecutor = new StubExecutor(EnumSet.of(AgentType.PLAN_EXECUTOR));

        AgentExecutorRegistry registry = new AgentExecutorRegistry(List.of(conversational, planExecutor));

        assertSame(conversational, registry.require(AgentType.REACT));
        assertSame(conversational, registry.require(AgentType.SUPERVISOR));
        assertSame(planExecutor, registry.require(AgentType.PLAN_EXECUTOR));
    }

    @Test
    void shouldFailFastWhenExecutorIsMissing() {
        AgentExecutor conversational = new StubExecutor(EnumSet.of(AgentType.REACT, AgentType.SUPERVISOR));

        assertThrows(IllegalStateException.class,
                () -> new AgentExecutorRegistry(List.of(conversational)));
    }

    @Test
    void shouldFailFastWhenExecutorRegistrationIsAmbiguous() {
        AgentExecutor conversational = new StubExecutor(EnumSet.of(AgentType.REACT, AgentType.SUPERVISOR));
        AgentExecutor duplicateReact = new StubExecutor(EnumSet.of(AgentType.REACT, AgentType.PLAN_EXECUTOR));

        assertThrows(IllegalStateException.class,
                () -> new AgentExecutorRegistry(List.of(conversational, duplicateReact)));
    }

    private record StubExecutor(Set<AgentType> supportedTypes) implements AgentExecutor {

        @Override
        public boolean supports(AgentType agentType) {
            return supportedTypes.contains(agentType);
        }

        @Override
        public CapabilityExecutionResult execute(AgentExecutionRequest request) {
            throw new UnsupportedOperationException("registry test does not execute agents");
        }
    }
}
