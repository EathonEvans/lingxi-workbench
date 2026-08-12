package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.model.AgentExecutionRequest;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidence;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.plan.PlannerRuntimeCoordinator;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import com.lingxi.workbench.infra.ai.agent.model.PlanExecutorPolicy;
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

class PlanExecutorAgentExecutorTest {

    @Test
    void shouldDelegateCompleteLockedContextToPlannerRuntime() {
        PlannerRuntimeCoordinator coordinator = mock(PlannerRuntimeCoordinator.class);
        PlanExecutorAgentExecutor executor = new PlanExecutorAgentExecutor(coordinator);
        ChatExecutionContext executionContext = mock(ChatExecutionContext.class);
        ConversationContext conversationContext = mock(ConversationContext.class);
        IntentRoutingEvidence evidence = mock(IntentRoutingEvidence.class);
        when(executionContext.getExecutionId()).thenReturn("run-1");
        when(executionContext.getConversationContext()).thenReturn(conversationContext);
        when(coordinator.execute(org.mockito.ArgumentMatchers.any())).thenReturn(
                new PlanExecutionResult("done", 21L, List.of()));
        ApplicationCapabilityDefinition capability = capability();
        AgentRuntimeSnapshot agent = planExecutorAgent(PlanExecutorPolicy.defaults(), null, List.of());

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
        assertThat(delegated.getPlanExecutor().getAgent()).isSameAs(agent);
        assertThat(delegated.getPlanExecutor().getCapability()).isSameAs(capability);
        assertThat(delegated.getPlanExecutor().getRoutingEvidence()).isSameAs(evidence);
        assertThat(delegated.confirmedCapabilityCodes()).containsExactly("refund_workflow");
    }

    @Test
    void shouldRejectAgentVersionThatBreaksPlanExecutorContract() {
        PlannerRuntimeCoordinator coordinator = mock(PlannerRuntimeCoordinator.class);
        PlanExecutorAgentExecutor executor = new PlanExecutorAgentExecutor(coordinator);
        AgentRuntimeSnapshot invalidAgent = planExecutorAgent(
                PlanExecutorPolicy.defaults(),
                99L,
                List.of("system_current_time"));

        assertThrows(BusinessException.class, () -> executor.execute(AgentExecutionRequest.builder()
                .capability(capability())
                .agent(invalidAgent)
                .query("process refund")
                .executionContext(mock(ChatExecutionContext.class))
                .build()));
    }

    private static AgentRuntimeSnapshot planExecutorAgent(
            PlanExecutorPolicy policy,
            Long userPromptVersionId,
            List<String> toolCodes) {
        return new AgentRuntimeSnapshot(10L, 11L, AgentRuntimeDefinition.builder()
                .schemaVersion(AGENT_SCHEMA_VERSION)
                .agentType(AgentType.PLAN_EXECUTOR)
                .chatModelInstanceId(21L)
                .systemPromptVersionId(22L)
                .userPromptVersionId(userPromptVersionId)
                .toolCodes(toolCodes)
                .planExecutorPolicy(policy)
                .build());
    }

    private static ApplicationCapabilityDefinition capability() {
        return ApplicationCapabilityDefinition.builder()
                .code("plan_executor")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(10L)
                .targetVersionId(11L)
                .build();
    }
}
