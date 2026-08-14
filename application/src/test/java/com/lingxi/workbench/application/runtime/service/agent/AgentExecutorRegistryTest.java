package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.service.agent.AgentExecutor;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentExecutorRegistryTest {

    @Test
    void shouldRequireExactlyOneExecutorForEveryAgentRole() {
        AgentExecutor specialist = new StubExecutor(EnumSet.of(AgentRole.SPECIALIST));
        AgentExecutor supervisor = new StubExecutor(EnumSet.of(AgentRole.SUPERVISOR));

        AgentExecutorRegistry registry = new AgentExecutorRegistry(List.of(specialist, supervisor));

        assertSame(specialist, registry.require(AgentRole.SPECIALIST));
        assertSame(supervisor, registry.require(AgentRole.SUPERVISOR));
    }

    @Test
    void shouldFailFastWhenExecutorIsMissing() {
        AgentExecutor specialist = new StubExecutor(EnumSet.of(AgentRole.SPECIALIST));

        assertThrows(IllegalStateException.class,
                () -> new AgentExecutorRegistry(List.of(specialist)));
    }

    @Test
    void shouldFailFastWhenExecutorRegistrationIsAmbiguous() {
        AgentExecutor specialist = new StubExecutor(EnumSet.of(AgentRole.SPECIALIST));
        AgentExecutor duplicate = new StubExecutor(EnumSet.of(AgentRole.SPECIALIST));
        AgentExecutor supervisor = new StubExecutor(EnumSet.of(AgentRole.SUPERVISOR));

        assertThrows(IllegalStateException.class,
                () -> new AgentExecutorRegistry(List.of(specialist, duplicate, supervisor)));
    }

    private record StubExecutor(Set<AgentRole> supportedTypes) implements AgentExecutor {

        @Override
        public boolean supports(AgentRole role) {
            return supportedTypes.contains(role);
        }

        @Override
        public CapabilityExecutionResult execute(AgentExecutionRequest request) {
            throw new UnsupportedOperationException("registry test does not execute agents");
        }
    }
}
