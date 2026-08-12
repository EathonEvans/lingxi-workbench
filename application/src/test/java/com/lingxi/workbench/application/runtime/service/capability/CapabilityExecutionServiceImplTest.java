package com.lingxi.workbench.application.runtime.service.capability;

import com.lingxi.workbench.application.runtime.service.agent.AgentExecutorRegistry;
import com.lingxi.workbench.application.runtime.service.agent.ConversationalAgentExecutor;

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
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.observation.ObservationRegistry;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(agentChatService.chat(snapshot, "问题", context)).thenReturn("回答");
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
        when(agentRuntimeService.resolveVersion(60L, 70L)).thenReturn(agentSnapshot());
        when(agentChatService.chat(agentSnapshot(), "问题", context)).thenReturn("超长回答");
        when(estimator.estimate("超长回答")).thenReturn(101);
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, checksum, estimator);

        assertThrows(BusinessException.class, () -> service.execute(
                request(agentCapability(checksum.calculate(70L), 2), context)));
    }

    @Test
    void shouldEnforceAgentCapabilityTimeout() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentRuntimeService agentRuntimeService = mock(AgentRuntimeService.class);
        when(agentRuntimeService.resolveVersion(60L, 70L)).thenReturn(agentSnapshot());
        AgentChatService slowAgent = mock(AgentChatService.class);
        when(slowAgent.chat(any(AgentRuntimeSnapshot.class), eq("问题"), any(ChatExecutionContext.class)))
                .thenAnswer(invocation -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return "迟到回答";
        });
        CapabilityExecutionServiceImpl service = service(
                slowAgent, agentRuntimeService, checksum, mock(TokenCountEstimator.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                request(agentCapability(checksum.calculate(70L), 1), context())));

        assertEquals("能力执行超时：consult_agent", exception.getMessage());
    }

    @Test
    void shouldRejectExpiredApplicationDeadlineBeforeCapabilityExecution() {
        AgentChatService agentChatService = mock(AgentChatService.class);
        AgentRuntimeService agentRuntimeService = mock(AgentRuntimeService.class);
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, checksum, mock(TokenCountEstimator.class));
        ChatExecutionContext context = ChatExecutionContext.builder()
                .executionId("expired-execution")
                .runtimeVariables(new RuntimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 1))
                .runtimePolicy(ApplicationRuntimePolicy.builder().timeoutSeconds(1).build())
                .startedAt(Instant.now().minusSeconds(2))
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                request(agentCapability(checksum.calculate(70L), 10), context)));

        assertEquals("Application单轮执行超过总超时", exception.getMessage());
        verifyNoInteractions(agentChatService, agentRuntimeService);
    }

    private static CapabilityExecutionServiceImpl service(AgentChatService agentChatService,
                                                          AgentRuntimeService agentRuntimeService,
                                                          ApplicationCapabilityChecksum checksum,
                                                          TokenCountEstimator estimator) {
        AgentExecutorRegistry registry = mock(AgentExecutorRegistry.class);
        when(registry.require(AgentType.REACT)).thenReturn(new ConversationalAgentExecutor(agentChatService));
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentExecutorRegistry> registryProvider = mock(ObjectProvider.class);
        when(registryProvider.getObject()).thenReturn(registry);
        return new CapabilityExecutionServiceImpl(
                registryProvider,
                agentRuntimeService,
                mock(WorkflowRuntimeRepo.class),
                (definition, request) -> {
                    throw new AssertionError("Agent测试不应进入Workflow");
                },
                mock(ToolRuntimeRepo.class),
                mock(ToolRuntimeService.class),
                checksum,
                estimator,
                ObservationRegistry.NOOP);
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
                .build();
    }

    private static AgentRuntimeSnapshot agentSnapshot() {
        return new AgentRuntimeSnapshot(60L, 70L, AgentRuntimeDefinition.builder()
                .schemaVersion(6)
                .agentType(AgentType.REACT)
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
