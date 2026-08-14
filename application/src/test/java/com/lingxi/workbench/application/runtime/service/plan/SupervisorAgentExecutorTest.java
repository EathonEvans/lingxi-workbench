package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidence;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupervisorAgentExecutorTest {

    @Test
    void shouldDelegateCompleteLockedContextToPlannerRuntime() {
        PlannerRuntimeCoordinator coordinator = mock(PlannerRuntimeCoordinator.class);
        SupervisorAgentExecutor executor = new SupervisorAgentExecutor(coordinator);
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        ConversationContext conversationContext = mock(ConversationContext.class);
        IntentRoutingEvidence evidence = mock(IntentRoutingEvidence.class);
        when(executionContext.getExecutionId()).thenReturn("run-1");
        when(executionContext.getConversationContext()).thenReturn(conversationContext);
        when(coordinator.execute(org.mockito.ArgumentMatchers.any())).thenReturn(
                new PlanExecutionResult("done", 21L, List.of()));
        ApplicationCapabilityDefinition capability = capability();
        AgentRuntimeSnapshot agent = supervisorAgent(PlanPolicy.defaults(), null, List.of());

        CapabilityExecutionResult result = executor.execute(AgentExecutionRequest.builder()
                .capability(capability)
                .agent(agent)
                .query("process refund")
                .executionContext(executionContext)
                .routingEvidence(evidence)
                .confirmedCapabilityCodes(Set.of("refund_workflow"))
                .build());

        assertThat(result.content()).isEqualTo("done");
        assertThat(result.modelInstanceId()).isEqualTo(21L);
        ArgumentCaptor<PlannerRuntimeRequest> captor = ArgumentCaptor.forClass(PlannerRuntimeRequest.class);
        verify(coordinator).execute(captor.capture());
        PlannerRuntimeRequest delegated = captor.getValue();
        assertThat(delegated.getRunId()).isEqualTo("run-1");
        assertThat(delegated.getConversationContext()).isSameAs(conversationContext);
        assertThat(delegated.getExecutionContext()).isSameAs(executionContext);
        assertThat(delegated.getSupervisor().getAgent()).isSameAs(agent);
        assertThat(delegated.getSupervisor().getCapability()).isSameAs(capability);
        assertThat(delegated.getSupervisor().getRoutingEvidence()).isSameAs(evidence);
        assertThat(delegated.confirmedCapabilityCodes()).containsExactly("refund_workflow");
    }

    @Test
    void shouldRejectAgentVersionThatBreaksSupervisorContract() {
        PlannerRuntimeCoordinator coordinator = mock(PlannerRuntimeCoordinator.class);
        SupervisorAgentExecutor executor = new SupervisorAgentExecutor(coordinator);
        AgentRuntimeSnapshot invalidAgent = supervisorAgent(
                PlanPolicy.defaults(),
                99L,
                List.of("system_current_time"));

        assertThrows(BusinessException.class, () -> executor.execute(AgentExecutionRequest.builder()
                .capability(capability())
                .agent(invalidAgent)
                .query("process refund")
                .executionContext(mock(ChatExecutionContext.class))
                .build()));
    }

    private static AgentRuntimeSnapshot supervisorAgent(
            PlanPolicy policy,
            Long userPromptVersionId,
            List<String> toolCodes) {
        return new AgentRuntimeSnapshot(10L, 11L, AgentRuntimeDefinition.builder()
                .schemaVersion(AGENT_SCHEMA_VERSION)
                .role(AgentRole.SUPERVISOR)
                .chatModelInstanceId(21L)
                .systemPromptVersionId(22L)
                .userPromptVersionId(userPromptVersionId)
                .toolCodes(toolCodes)
                .planPolicy(policy)
                .build());
    }

    private static ApplicationCapabilityDefinition capability() {
        return ApplicationCapabilityDefinition.builder()
                .code("supervisor")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(10L)
                .targetVersionId(11L)
                .build();
    }
}
