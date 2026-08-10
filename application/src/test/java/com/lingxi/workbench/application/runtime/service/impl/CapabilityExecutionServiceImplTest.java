package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.WorkflowRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.AgentChatService;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.ToolRuntimeService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import io.micrometer.observation.ObservationRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        when(agentChatService.chat(60L, 70L, "问题", context)).thenReturn("回答");
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, checksum, estimator);

        CapabilityExecutionResult result = service.execute(
                new CapabilityExecutionRequest(capability, JSONObject.of("query", "问题"), context));

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

        assertThrows(BusinessException.class, () -> service.execute(new CapabilityExecutionRequest(
                agentCapability("changed", 2), JSONObject.of("query", "问题"), context())));
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
        when(agentChatService.chat(60L, 70L, "问题", context)).thenReturn("超长回答");
        when(estimator.estimate("超长回答")).thenReturn(101);
        CapabilityExecutionServiceImpl service = service(
                agentChatService, agentRuntimeService, checksum, estimator);

        assertThrows(BusinessException.class, () -> service.execute(new CapabilityExecutionRequest(
                agentCapability(checksum.calculate(70L), 2), JSONObject.of("query", "问题"), context)));
    }

    @Test
    void shouldEnforceAgentCapabilityTimeout() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentRuntimeService agentRuntimeService = mock(AgentRuntimeService.class);
        when(agentRuntimeService.resolveVersion(60L, 70L)).thenReturn(agentSnapshot());
        AgentChatService slowAgent = (agentId, versionId, query, context) -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return "迟到回答";
        };
        CapabilityExecutionServiceImpl service = service(
                slowAgent, agentRuntimeService, checksum, mock(TokenCountEstimator.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.execute(
                new CapabilityExecutionRequest(agentCapability(checksum.calculate(70L), 1),
                        JSONObject.of("query", "问题"), context())));

        assertEquals("能力执行超时：consult_agent", exception.getMessage());
    }

    private static CapabilityExecutionServiceImpl service(AgentChatService agentChatService,
                                                          AgentRuntimeService agentRuntimeService,
                                                          ApplicationCapabilityChecksum checksum,
                                                          TokenCountEstimator estimator) {
        return new CapabilityExecutionServiceImpl(
                agentChatService,
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
                .type(ApplicationCapabilityType.REACT_AGENT)
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
                .chatModelInstanceId(99L)
                .build());
    }

    private static ChatExecutionContext context() {
        return ChatExecutionContext.builder()
                .executionId("execution-1")
                .runtimeVariables(new RuntimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 1))
                .build();
    }
}
