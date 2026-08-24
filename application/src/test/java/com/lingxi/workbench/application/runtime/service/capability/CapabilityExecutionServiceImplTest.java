package com.lingxi.workbench.application.runtime.service.capability;

import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.application.runtime.service.agent.AgentExecutorRegistry;
import com.lingxi.workbench.application.runtime.service.agent.SpecialistAgentExecutor;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.WorkflowRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.agent.AgentChatService;
import com.lingxi.workbench.application.runtime.service.agent.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.service.impl.ApplicationWorkflowExecutionPortsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.observation.ObservationRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class CapabilityExecutionServiceImplTest {

    @Test
    void shouldExecuteLockedAgentAndReturnModelInstance() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentChatService agentChatService = mock(AgentChatService.class);
        AgentRuntimeService agentRuntimeService = mock(AgentRuntimeService.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ApplicationCapabilityDefinition capability = agentCapability(checksum.calculate(70L), 2);
        AgentRuntimeSnapshot snapshot = agentSnapshot();
        when(agentRuntimeService.resolveVersion(60L, 70L)).thenReturn(snapshot);
        when(estimator.estimate("回答")).thenReturn(20);
        ChatExecutionContext context = context();
        when(agentChatService.chat(snapshot, "问题", context)).thenReturn(completed("回答"));
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, checksum, estimator);

        CapabilityExecutionResult result = service.execute(request(capability, context));

        assertEquals("回答", result.content());
        assertEquals(99L, result.modelInstanceId());
    }

    @Test
    void shouldRejectChangedAgentVersionChecksumBeforeChat() {
        AgentChatService agentChatService = mock(AgentChatService.class);
        AgentRuntimeService agentRuntimeService = mock(AgentRuntimeService.class);
        when(agentRuntimeService.resolveVersion(60L, 70L)).thenReturn(agentSnapshot());
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, new ApplicationCapabilityChecksum(), mock(TokenCountEstimator.class));

        assertThrows(BusinessException.class, () -> service.execute(
                request(agentCapability("changed", 2), context())));
        verifyNoInteractions(agentChatService);
    }

    @Test
    void shouldRejectResultOverPublishedTokenBudget() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentChatService agentChatService = mock(AgentChatService.class);
        AgentRuntimeService agentRuntimeService = mock(AgentRuntimeService.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ChatExecutionContext context = context();
        AgentRuntimeSnapshot snapshot = agentSnapshot();
        when(agentRuntimeService.resolveVersion(60L, 70L)).thenReturn(snapshot);
        when(agentChatService.chat(snapshot, "问题", context)).thenReturn(completed("超长回答"));
        when(estimator.estimate("超长回答")).thenReturn(101);
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, checksum, estimator);

        assertThrows(BusinessException.class, () -> service.execute(
                request(agentCapability(checksum.calculate(70L), 2), context)));
    }

    @Test
    void shouldRequireReconciliationWhenWriteAgentCompletedWithOversizedResult() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentChatService agentChatService = mock(AgentChatService.class);
        AgentRuntimeService agentRuntimeService = mock(AgentRuntimeService.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ChatExecutionContext context = context();
        AgentRuntimeSnapshot snapshot = agentSnapshot();
        when(agentRuntimeService.resolveVersion(60L, 70L)).thenReturn(snapshot);
        when(agentChatService.chat(snapshot, "问题", context)).thenReturn(completed("写操作已完成"));
        when(estimator.estimate("写操作已完成")).thenReturn(101);
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, checksum, estimator);
        ApplicationCapabilityDefinition capability = agentCapability(checksum.calculate(70L), 2);
        capability.setRiskLevel(CapabilityRiskLevel.WRITE);

        assertThrows(AgentReconciliationPendingException.class,
                () -> service.execute(request(capability, context)));
    }

    @Test
    void shouldExecuteCapabilityWithoutApplicationWallClockDeadline() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentRuntimeService agentRuntimeService = mock(AgentRuntimeService.class);
        when(agentRuntimeService.resolveVersion(60L, 70L)).thenReturn(agentSnapshot());
        AgentChatService agentChatService = mock(AgentChatService.class);
        when(agentChatService.chat(any(AgentRuntimeSnapshot.class), eq("问题"), any(ChatExecutionContext.class)))
                .thenReturn(completed("正常回答"));
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        when(estimator.estimate("正常回答")).thenReturn(10);
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, checksum, estimator);

        CapabilityExecutionResult result = service.execute(
                request(agentCapability(checksum.calculate(70L), 1), context()));

        assertEquals("正常回答", result.content());
    }

    private static CapabilityExecutionServiceImpl service(AgentChatService agentChatService,
                                                          AgentRuntimeService agentRuntimeService,
                                                          ApplicationCapabilityChecksum checksum,
                                                          TokenCountEstimator estimator) {
        AgentExecutorRegistry registry = mock(AgentExecutorRegistry.class);
        when(registry.require(AgentRole.SPECIALIST)).thenReturn(new SpecialistAgentExecutor(agentChatService));
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentExecutorRegistry> registryProvider = mock(ObjectProvider.class);
        when(registryProvider.getObject()).thenReturn(registry);
        return new CapabilityExecutionServiceImpl(
                registryProvider,
                agentRuntimeService,
                mock(WorkflowRuntimeRepo.class),
                (definition, request, ports) -> {
                    throw new AssertionError("Agent测试不应进入Workflow");
                },
                mock(ApplicationWorkflowExecutionPortsFactory.class),
                mock(ToolRuntimeRepo.class),
                mock(ToolRuntimeService.class),
                checksum,
                estimator,
                ObservationRegistry.NOOP);
    }

    private static com.lingxi.workbench.agentruntime.model.AgentRunResult completed(String content) {
        return new com.lingxi.workbench.agentruntime.model.AgentRunResult.Completed(
                content, com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage.empty(), 0L);
    }

    private static ApplicationCapabilityDefinition agentCapability(String checksum, int timeoutSeconds) {
        return ApplicationCapabilityDefinition.builder()
                .code("consult_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(60L)
                .targetVersionId(70L)
                .configurationChecksum(checksum)
                .timeoutSeconds(timeoutSeconds)
                .maxCalls(1)
                .resultTokenBudget(100)
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .build();
    }

    private static AgentRuntimeSnapshot agentSnapshot() {
        return new AgentRuntimeSnapshot(60L, 70L, AgentRuntimeDefinition.builder()
                .schemaVersion(AGENT_SCHEMA_VERSION)
                .role(AgentRole.SPECIALIST)
                .chatModelInstanceId(99L)
                .toolCodes(List.of())
                .build());
    }

    private static CapabilityExecutionRequest request(
            ApplicationCapabilityDefinition capability, ChatExecutionContext context) {
        return CapabilityExecutionRequest.builder()
                .capability(capability)
                .inputs(JSONObject.of("query", "问题"))
                .context(context)
                .build();
    }

    private static ChatExecutionContext context() {
        return ChatExecutionContext.builder()
                .executionId("execution-1")
                .runtimeVariables(new RuntimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 1))
                .build();
    }
}
