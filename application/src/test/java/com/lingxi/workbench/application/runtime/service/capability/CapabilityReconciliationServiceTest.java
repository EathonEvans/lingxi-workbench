package com.lingxi.workbench.application.runtime.service.capability;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.service.execution.RedisLockExecutor;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolReconciliation;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationResult;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationStatus;
import com.lingxi.workbench.infra.ai.tool.model.ToolSecurityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapabilityReconciliationServiceTest {

    private final CapabilityInvocationRepo invocationRepo = mock(CapabilityInvocationRepo.class);
    private final ExecutionRunRepo runRepo = mock(ExecutionRunRepo.class);
    private final ToolRuntimeService toolRuntimeService = mock(ToolRuntimeService.class);
    private final RedisLockExecutor redisLockExecutor = mock(RedisLockExecutor.class);
    private final CapabilityReconciliationTxService reconciliationTxService = mock(CapabilityReconciliationTxService.class);
    private final CapabilityReconciliationProperties properties = new CapabilityReconciliationProperties();
    private CapabilityReconciliationService service;

    @BeforeEach
    void setUp() {
        properties.setMaxAttempts(3);
        properties.setBatchSize(10);
        properties.setClaimLease(Duration.ofSeconds(30));
        properties.setRetryDelay(Duration.ofMinutes(1));
        service = new CapabilityReconciliationService(invocationRepo, runRepo, toolRuntimeService,
                redisLockExecutor, reconciliationTxService, properties);
        when(redisLockExecutor.tryExecute(anyString(), any(Duration.class), any(Duration.class), any()))
                .thenAnswer(call -> Optional.of(((Supplier<?>) call.getArgument(3)).get()));
    }

    @Test
    void shouldCompleteRunAsPartialWhenExternalToolConfirmsSuccess() {
        CapabilityInvocationEntity invocation = invocation(1);
        when(invocationRepo.findReconciliationCandidates(any(OffsetDateTime.class), anyInt())).thenReturn(List.of(invocation));
        when(invocationRepo.claimReconciliationAttempt(any(), anyInt(), any(OffsetDateTime.class))).thenReturn(true);
        when(invocationRepo.findById(20L)).thenReturn(Optional.of(invocation));
        when(runRepo.findByRunId("run-1")).thenReturn(Optional.of(run()));
        AgentTool tool = reconciliableTool(
                new AgentToolReconciliationResult(AgentToolReconciliationStatus.SUCCEEDED, "receipt-1", null));
        when(toolRuntimeService.resolveActiveTools(List.of("send_mail"))).thenReturn(List.of(tool));

        assertEquals(1, service.reconcile());

        verify(reconciliationTxService).completeSucceeded(20L, "receipt-1");
    }

    @Test
    void shouldStopAutomaticAttemptsWhenRemoteRequiresCompensation() {
        CapabilityInvocationEntity invocation = invocation(1);
        when(invocationRepo.findReconciliationCandidates(any(OffsetDateTime.class), anyInt())).thenReturn(List.of(invocation));
        when(invocationRepo.claimReconciliationAttempt(any(), anyInt(), any(OffsetDateTime.class))).thenReturn(true);
        when(invocationRepo.findById(20L)).thenReturn(Optional.of(invocation));
        when(runRepo.findByRunId("run-1")).thenReturn(Optional.of(run()));
        AgentTool tool = reconciliableTool(
                new AgentToolReconciliationResult(AgentToolReconciliationStatus.COMPENSATION_REQUIRED, null, null));
        when(toolRuntimeService.resolveActiveTools(List.of("send_mail"))).thenReturn(List.of(tool));

        service.reconcile();

        verify(invocationRepo).suspendReconciliation(20L, "TOOL_RECONCILIATION_MANUAL_COMPENSATION");
    }

    @Test
    void shouldStopAutomaticAttemptsAfterConfiguredLimit() {
        CapabilityInvocationEntity invocation = invocation(3);
        when(invocationRepo.findReconciliationCandidates(any(OffsetDateTime.class), anyInt())).thenReturn(List.of(invocation));
        when(invocationRepo.claimReconciliationAttempt(any(), anyInt(), any(OffsetDateTime.class))).thenReturn(true);
        when(invocationRepo.findById(20L)).thenReturn(Optional.of(invocation));
        when(runRepo.findByRunId("run-1")).thenReturn(Optional.of(run()));
        AgentTool tool = reconciliableTool(
                new AgentToolReconciliationResult(AgentToolReconciliationStatus.PENDING, null, "REMOTE_PENDING"));
        when(toolRuntimeService.resolveActiveTools(List.of("send_mail"))).thenReturn(List.of(tool));

        service.reconcile();

        verify(invocationRepo).suspendReconciliation(20L, "TOOL_RECONCILIATION_MANUAL_REVIEW");
    }

    private static AgentTool reconciliableTool(AgentToolReconciliationResult result) {
        AgentTool tool = mock(AgentTool.class);
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("send_mail")
                .displayName("发送邮件")
                .description("发送邮件")
                .executionMode(AgentToolExecutionMode.SIDE_EFFECT)
                .securityPolicy(ToolSecurityPolicy.defaults())
                .configurationHash("tool-config")
                .build();
        AgentToolReconciliation reconciliation = request -> result;
        when(tool.definition()).thenReturn(definition);
        when(tool.reconciliation()).thenReturn(Optional.of(reconciliation));
        return tool;
    }

    private static CapabilityInvocationEntity invocation(int attempts) {
        CapabilityInvocationEntity invocation = new CapabilityInvocationEntity();
        invocation.setId(20L);
        invocation.setRunId("run-1");
        invocation.setToolCode("send_mail");
        invocation.setToolInput(JSONObject.of("to", "member@example.com"));
        invocation.setIdempotencyKey("approval-event");
        invocation.setToolConfigurationHash("tool-config");
        invocation.setStatus(CapabilityInvocationStatus.RECONCILIATION_PENDING);
        invocation.setReconciliationAttempts(attempts);
        invocation.setNextReconcileAt(OffsetDateTime.now());
        return invocation;
    }

    private static ExecutionRunEntity run() {
        ExecutionRunEntity run = new ExecutionRunEntity();
        run.setRunId("run-1");
        run.setUserId(7L);
        run.setConversationId(8L);
        run.setAssistantMessageId(9L);
        run.setStatus(ExecutionRunStatus.RECONCILIATION_PENDING);
        return run;
    }
}
