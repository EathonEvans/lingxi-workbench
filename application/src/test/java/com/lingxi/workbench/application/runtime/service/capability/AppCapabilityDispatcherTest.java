package com.lingxi.workbench.application.runtime.service.capability;

import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ApplicationRun;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.WorkflowRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.agent.AgentExecutionAdapter;
import com.lingxi.workbench.application.runtime.service.agent.AgentVersionService;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.service.impl.ApplicationWorkflowExecutionPortsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;
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

class AppCapabilityDispatcherTest {

    @Test
    void shouldExecuteLockedAgentAndReturnModelInstance() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentExecutionAdapter agentExecutionAdapter = mock(AgentExecutionAdapter.class);
        AgentVersionService agentVersionService = mock(AgentVersionService.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        CapabilityDefinition capability = agentCapability(checksum.calculate(70L), 2);
        AgentRuntimeSnapshot snapshot = agentSnapshot();
        when(agentVersionService.resolveVersion(60L, 70L)).thenReturn(snapshot);
        when(estimator.estimate("回答")).thenReturn(20);
        ApplicationRun run = run();
        when(agentExecutionAdapter.executeSpecialist(any(CapabilityExecutionRequest.class), eq(snapshot)))
                .thenReturn(new CapabilityExecutionResult("回答", 99L, List.of()));
        AppCapabilityDispatcher service = service(
                agentExecutionAdapter, agentVersionService, checksum, estimator);

        CapabilityExecutionResult result = service.execute(request(capability, run));

        assertEquals("回答", result.content());
        assertEquals(99L, result.modelInstanceId());
    }

    @Test
    void shouldRejectChangedAgentVersionChecksumBeforeChat() {
        AgentExecutionAdapter agentExecutionAdapter = mock(AgentExecutionAdapter.class);
        AgentVersionService agentVersionService = mock(AgentVersionService.class);
        when(agentVersionService.resolveVersion(60L, 70L)).thenReturn(agentSnapshot());
        AppCapabilityDispatcher service = service(
                agentExecutionAdapter, agentVersionService, new ApplicationCapabilityChecksum(), mock(TokenCountEstimator.class));

        assertThrows(BusinessException.class, () -> service.execute(
                request(agentCapability("changed", 2), run())));
        verifyNoInteractions(agentExecutionAdapter);
    }

    @Test
    void shouldRejectResultOverPublishedTokenBudget() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentExecutionAdapter agentExecutionAdapter = mock(AgentExecutionAdapter.class);
        AgentVersionService agentVersionService = mock(AgentVersionService.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ApplicationRun run = run();
        AgentRuntimeSnapshot snapshot = agentSnapshot();
        when(agentVersionService.resolveVersion(60L, 70L)).thenReturn(snapshot);
        when(agentExecutionAdapter.executeSpecialist(any(CapabilityExecutionRequest.class), eq(snapshot)))
                .thenReturn(new CapabilityExecutionResult("超长回答", 99L, List.of()));
        when(estimator.estimate("超长回答")).thenReturn(101);
        AppCapabilityDispatcher service = service(
                agentExecutionAdapter, agentVersionService, checksum, estimator);

        assertThrows(BusinessException.class, () -> service.execute(
                request(agentCapability(checksum.calculate(70L), 2), run)));
    }

    @Test
    void shouldRequireReconciliationWhenWriteAgentCompletedWithOversizedResult() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentExecutionAdapter agentExecutionAdapter = mock(AgentExecutionAdapter.class);
        AgentVersionService agentVersionService = mock(AgentVersionService.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ApplicationRun run = run();
        AgentRuntimeSnapshot snapshot = agentSnapshot();
        when(agentVersionService.resolveVersion(60L, 70L)).thenReturn(snapshot);
        when(agentExecutionAdapter.executeSpecialist(any(CapabilityExecutionRequest.class), eq(snapshot)))
                .thenReturn(new CapabilityExecutionResult("写操作已完成", 99L, List.of()));
        when(estimator.estimate("写操作已完成")).thenReturn(101);
        AppCapabilityDispatcher service = service(
                agentExecutionAdapter, agentVersionService, checksum, estimator);
        CapabilityDefinition capability = agentCapability(checksum.calculate(70L), 2);
        capability.setRiskLevel(CapabilityRiskLevel.WRITE);

        assertThrows(AgentReconciliationPendingException.class,
                () -> service.execute(request(capability, run)));
    }

    @Test
    void shouldExecuteCapabilityWithoutApplicationWallClockDeadline() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentVersionService agentVersionService = mock(AgentVersionService.class);
        when(agentVersionService.resolveVersion(60L, 70L)).thenReturn(agentSnapshot());
        AgentExecutionAdapter agentExecutionAdapter = mock(AgentExecutionAdapter.class);
        when(agentExecutionAdapter.executeSpecialist(any(CapabilityExecutionRequest.class), any(AgentRuntimeSnapshot.class)))
                .thenReturn(new CapabilityExecutionResult("正常回答", 99L, List.of()));
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        when(estimator.estimate("正常回答")).thenReturn(10);
        AppCapabilityDispatcher service = service(
                agentExecutionAdapter, agentVersionService, checksum, estimator);

        CapabilityExecutionResult result = service.execute(
                request(agentCapability(checksum.calculate(70L), 1), run()));

        assertEquals("正常回答", result.content());
    }

    private static AppCapabilityDispatcher service(AgentExecutionAdapter agentExecutionAdapter,
                                                          AgentVersionService agentVersionService,
                                                          ApplicationCapabilityChecksum checksum,
                                                          TokenCountEstimator estimator) {
        return new AppCapabilityDispatcher(
                agentExecutionAdapter,
                agentVersionService,
                mock(WorkflowRuntimeRepo.class),
                (definition, request, ports) -> {
                    throw new AssertionError("Agent测试不应进入Workflow");
                },
                mock(ApplicationWorkflowExecutionPortsFactory.class),
                mock(ToolRuntimeRepo.class),
                mock(ToolRuntimeService.class),
                checksum,
                estimator,
                ObservationRegistry.NOOP,
                mock(com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier.class),
                mock(com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService.class));
    }

    private static CapabilityDefinition agentCapability(String checksum, int timeoutSeconds) {
        return CapabilityDefinition.builder()
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
            CapabilityDefinition capability, ApplicationRun run) {
        return CapabilityExecutionRequest.builder()
                .capability(capability)
                .inputs(JSONObject.of("query", "问题"))
                .run(run)
                .build();
    }

    private static ApplicationRun run() {
        return mock(ApplicationRun.class);
    }
}
