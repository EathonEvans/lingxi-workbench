package com.lingxi.workbench.application.runtime.service.capability;

import com.lingxi.workbench.agentruntime.model.AgentReconciliationPendingException;
import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapability;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityInvocation;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityRisk;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityType;
import com.lingxi.workbench.agentruntime.plan.RuntimeTaskStep;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationChatExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationExecutionBudget;
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
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.service.impl.ApplicationWorkflowExecutionPortsFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import io.micrometer.observation.ObservationRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void shouldKeepDelegatedAgentObservationUnderSupervisorTraceAcrossThreadSwitch() {
        ApplicationCapabilityChecksum checksum = new ApplicationCapabilityChecksum();
        AgentExecutionAdapter agentExecutionAdapter = mock(AgentExecutionAdapter.class);
        AgentVersionService agentVersionService = mock(AgentVersionService.class);
        TokenCountEstimator estimator = mock(TokenCountEstimator.class);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        CapabilityDefinition supervisorCapability = agentCapability(
                "default_agent", 60L, 70L, checksum.calculate(70L));
        CapabilityDefinition delegatedCapability = agentCapability(
                "career_agent", 61L, 71L, checksum.calculate(71L));
        AgentRuntimeSnapshot supervisor = agentSnapshot(60L, 70L, AgentRole.SUPERVISOR);
        AgentRuntimeSnapshot specialist = agentSnapshot(61L, 71L, AgentRole.SPECIALIST);
        when(agentVersionService.resolveVersion(60L, 70L)).thenReturn(supervisor);
        when(agentVersionService.resolveVersion(61L, 71L)).thenReturn(specialist);
        when(estimator.estimate("子能力回答")).thenReturn(10);
        ApplicationRun run = supervisorRun(supervisorCapability, delegatedCapability);
        AtomicReference<Observation> supervisorObservation = new AtomicReference<>();
        AtomicReference<Observation> delegatedObservation = new AtomicReference<>();
        when(agentExecutionAdapter.executeSpecialist(any(CapabilityExecutionRequest.class), eq(specialist)))
                .thenAnswer(invocation -> {
                    delegatedObservation.set(registry.getCurrentObservation());
                    return new CapabilityExecutionResult("子能力回答", 99L, List.of());
                });
        when(agentExecutionAdapter.executeSupervisor(any(CapabilityExecutionRequest.class), eq(supervisor), any()))
                .thenAnswer(invocation -> {
                    supervisorObservation.set(registry.getCurrentObservation());
                    com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityInvoker invoker = invocation.getArgument(2);
                    String content = CompletableFuture.supplyAsync(() -> invoker.invoke(
                            delegatedInvocation(delegatedCapability))).join().getContent();
                    return new CapabilityExecutionResult(content, null, List.of());
                });
        AppCapabilityDispatcher service = service(agentExecutionAdapter, agentVersionService, checksum, estimator,
                registry);

        CapabilityExecutionResult result = service.execute(request(supervisorCapability, run));

        assertEquals("子能力回答", result.content());
        assertSame(supervisorObservation.get(), delegatedObservation.get().getContext().getParentObservation());
    }

    private static AppCapabilityDispatcher service(AgentExecutionAdapter agentExecutionAdapter,
                                                          AgentVersionService agentVersionService,
                                                          ApplicationCapabilityChecksum checksum,
                                                          TokenCountEstimator estimator) {
        return service(agentExecutionAdapter, agentVersionService, checksum, estimator, ObservationRegistry.NOOP);
    }

    private static AppCapabilityDispatcher service(AgentExecutionAdapter agentExecutionAdapter,
                                                    AgentVersionService agentVersionService,
                                                    ApplicationCapabilityChecksum checksum,
                                                    TokenCountEstimator estimator,
                                                    ObservationRegistry observationRegistry) {
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
                observationRegistry,
                mock(com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier.class),
                mock(com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService.class));
    }

    private static CapabilityDefinition agentCapability(String checksum, int timeoutSeconds) {
        return agentCapability("consult_agent", 60L, 70L, checksum, timeoutSeconds);
    }

    private static CapabilityDefinition agentCapability(String code, Long targetId, Long targetVersionId,
                                                        String checksum) {
        return agentCapability(code, targetId, targetVersionId, checksum, 2);
    }

    private static CapabilityDefinition agentCapability(String code, Long targetId, Long targetVersionId,
                                                        String checksum, int timeoutSeconds) {
        return CapabilityDefinition.builder()
                .code(code)
                .type(ApplicationCapabilityType.AGENT)
                .targetId(targetId)
                .targetVersionId(targetVersionId)
                .configurationChecksum(checksum)
                .timeoutSeconds(timeoutSeconds)
                .maxCalls(1)
                .resultTokenBudget(100)
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .build();
    }

    private static AgentRuntimeSnapshot agentSnapshot() {
        return agentSnapshot(60L, 70L, AgentRole.SPECIALIST);
    }

    private static AgentRuntimeSnapshot agentSnapshot(Long agentId, Long versionId, AgentRole role) {
        return new AgentRuntimeSnapshot(agentId, versionId, AgentRuntimeDefinition.builder()
                .schemaVersion(AGENT_SCHEMA_VERSION)
                .role(role)
                .chatModelInstanceId(99L)
                .toolCodes(List.of())
                .build());
    }

    private static ApplicationRun supervisorRun(CapabilityDefinition supervisor,
                                                CapabilityDefinition delegated) {
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .fallback(new FallbackAgent(supervisor.getCode()))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(supervisor, delegated)).build())
                .build();
        com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot application =
                mock(com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot.class);
        when(application.definition()).thenReturn(definition);
        ApplicationChatExecutionRequest executionRequest = mock(ApplicationChatExecutionRequest.class);
        when(executionRequest.getApplication()).thenReturn(application);
        when(executionRequest.getEventPublisher()).thenReturn(
                com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher.noop());
        ApplicationRun run = mock(ApplicationRun.class);
        when(run.executionId()).thenReturn("run-observation");
        when(run.request()).thenReturn(executionRequest);
        when(run.budget()).thenReturn(
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()));
        return run;
    }

    private static RuntimeCapabilityInvocation delegatedInvocation(CapabilityDefinition capability) {
        RuntimeCapability runtimeCapability = RuntimeCapability.builder().code(capability.getCode())
                .name("求职教练").type(RuntimeCapabilityType.AGENT).targetId(capability.getTargetId())
                .risk(RuntimeCapabilityRisk.READ_ONLY).maxCalls(1).resultTokenBudget(100).build();
        RuntimeTaskStep step = RuntimeTaskStep.builder().id("step-1").name("总结能力")
                .capabilityCode(capability.getCode()).build();
        AgentInvocationState state = new AgentInvocationState("run-observation", 1, 1, step,
                runtimeCapability, Map.of("query", "总结能力"), "fingerprint");
        return RuntimeCapabilityInvocation.builder().capability(runtimeCapability)
                .inputs(Map.of("query", "总结能力")).invocationState(state)
                .confirmedCapabilityCodes(Set.of()).executionRecorder(ignored -> { }).build();
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
