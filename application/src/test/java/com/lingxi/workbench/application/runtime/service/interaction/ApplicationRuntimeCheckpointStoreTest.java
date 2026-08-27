package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeInterruptionRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.execution.RedisLockExecutor;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.common.enums.ToolRiskLevel;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.ToolSecurityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class ApplicationRuntimeCheckpointStoreTest {

    private final InteractionSessionRepo interactionRepo = mock(InteractionSessionRepo.class);
    private final ExecutionCheckpointRepo checkpointRepo = mock(ExecutionCheckpointRepo.class);
    private final CapabilityInvocationRepo invocationRepo = mock(CapabilityInvocationRepo.class);
    private final ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
    private final RedisLockExecutor redisLockExecutor = mock(RedisLockExecutor.class);
    private ApplicationRuntimeCheckpointStore service;

    @BeforeEach
    void setUp() {
        service = new ApplicationRuntimeCheckpointStore(interactionRepo, checkpointRepo, invocationRepo,
                persistenceService, redisLockExecutor);
    }

    @Test
    void shouldPersistAgentScopeCheckpointBeforeReturningInterruption() {
        AgentScopeCheckpoint checkpoint = checkpoint();
        when(interactionRepo.findAnyPending(8L, 7L, 30L)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            invocation.<ExecutionCheckpointEntity>getArgument(0).setId(30L);
            return null;
        }).when(checkpointRepo).insert(any(ExecutionCheckpointEntity.class));
        doAnswer(invocation -> {
            invocation.<CapabilityInvocationEntity>getArgument(0).setId(20L);
            return null;
        }).when(invocationRepo).insert(any(CapabilityInvocationEntity.class));
        doAnswer(invocation -> {
            invocation.<InteractionSessionEntity>getArgument(0).setId(10L);
            return null;
        }).when(interactionRepo).insert(any(InteractionSessionEntity.class));
        when(invocationRepo.bindInteraction(20L, 10L)).thenReturn(true);
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("send_mail").displayName("发送邮件").description("发送外部邮件")
                .configurationHash("mail-v1")
                .securityPolicy(ToolSecurityPolicy.builder().approvalRequired(true)
                        .riskLevel(ToolRiskLevel.HIGH).resourceScope("用户邮箱").build())
                .build();

        RuntimeInterruption interruption = service.interrupt(new AgentScopeInterruptionRequest(
                checkpoint, definition, AgentToolExecutionContext.builder()
                        .executionId("run-1").userId(7L).conversationId(8L).assistantMessageId(9L).build()));

        assertEquals(10L, interruption.getInteractionId());
        assertEquals(30L, interruption.getCheckpointId());
        org.mockito.ArgumentCaptor<ExecutionCheckpointEntity> captor =
                org.mockito.ArgumentCaptor.forClass(ExecutionCheckpointEntity.class);
        verify(checkpointRepo).insert(captor.capture());
        assertEquals(AgentScopeCheckpoint.CURRENT_VERSION, captor.getValue().getCheckpointVersion());
        assertEquals(checkpoint, captor.getValue().getPayload());
        verify(persistenceService).changeRunStatus("run-1", ExecutionRunStatus.RUNNING,
                ExecutionRunStatus.WAITING_HUMAN, "TOOL_APPROVAL");
    }

    @Test
    void shouldRoundTripCompleteAgentScopeCheckpointPayload() {
        AgentScopeCheckpoint checkpoint = checkpoint();

        AgentScopeCheckpoint restored = JSON.parseObject(JSON.toJSONString(checkpoint), AgentScopeCheckpoint.class);

        assertEquals(checkpoint, restored);
    }

    @Test
    void shouldCloseInvocationCheckpointAndInteractionTogether() {
        CapabilityInvocationEntity invocation = invocation();
        when(invocationRepo.findById(20L)).thenReturn(Optional.of(invocation));
        when(invocationRepo.changeStatus(eq(20L), eq(CapabilityInvocationStatus.RUNNING),
                eq(CapabilityInvocationStatus.SUCCEEDED), eq("evt-1"), any(), eq(null))).thenReturn(true);
        when(checkpointRepo.consume(eq(30L), any(OffsetDateTime.class))).thenReturn(true);
        when(interactionRepo.complete(10L)).thenReturn(true);

        assertDoesNotThrow(() -> service.complete(20L, "done"));

        verify(interactionRepo).complete(10L);
    }

    @Test
    void shouldRejectPartialTerminalTransitionWhenCheckpointChanged() {
        CapabilityInvocationEntity invocation = invocation();
        when(invocationRepo.findById(20L)).thenReturn(Optional.of(invocation));
        when(invocationRepo.changeStatus(eq(20L), eq(CapabilityInvocationStatus.RUNNING),
                eq(CapabilityInvocationStatus.SUCCEEDED), eq("evt-1"), any(), eq(null))).thenReturn(true);
        when(checkpointRepo.consume(eq(30L), any(OffsetDateTime.class))).thenReturn(false);

        assertThrows(BusinessException.class, () -> service.complete(20L, "done"));

        verify(interactionRepo, never()).complete(10L);
    }

    @Test
    void shouldClaimApprovedInvocationInsideRunLockAndDatabaseCas() {
        AgentScopeCheckpoint checkpoint = checkpoint();
        InteractionSessionEntity interaction = new InteractionSessionEntity();
        interaction.setId(10L);
        interaction.setStatus(com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus.RESOLVED);
        interaction.setCheckpointId(30L);
        interaction.setRunId("run-1");
        interaction.setApplicationVersionId(30L);
        interaction.setAssistantMessageId(9L);
        interaction.setApprovalFingerprint("fingerprint");
        interaction.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        ExecutionCheckpointEntity stored = new ExecutionCheckpointEntity();
        stored.setId(30L);
        stored.setRunId("run-1");
        stored.setStatus(com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus.ACTIVE);
        stored.setCheckpointVersion(AgentScopeCheckpoint.CURRENT_VERSION);
        stored.setPayload(checkpoint);
        stored.setChecksum("fingerprint");
        stored.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        CapabilityInvocationEntity invocation = invocation();
        invocation.setStatus(CapabilityInvocationStatus.APPROVED);
        invocation.setApprovalFingerprint("fingerprint");
        invocation.setToolCode("send_mail");
        invocation.setToolInput(new com.lingxi.workbench.framework.json.JSONObject(Map.of()));
        when(interactionRepo.findOwned(10L, 8L, 7L)).thenReturn(Optional.of(interaction));
        when(checkpointRepo.findById(30L)).thenReturn(Optional.of(stored));
        when(invocationRepo.findByInteractionId(10L)).thenReturn(Optional.of(invocation));
        when(invocationRepo.changeStatus(20L, CapabilityInvocationStatus.APPROVED,
                CapabilityInvocationStatus.RUNNING, "evt-1", null, null)).thenReturn(true);
        when(redisLockExecutor.tryExecute(eq("lingxi:runtime:run:run-1"), any(), any(), any()))
                .thenAnswer(invocationCall -> Optional.of(((Supplier<?>) invocationCall.getArgument(3)).get()));

        assertEquals(20L, service.claimApproved(checkpoint, 10L, "fingerprint").invocationId());

        verify(redisLockExecutor).tryExecute(eq("lingxi:runtime:run:run-1"), any(), any(), any());
        verify(invocationRepo).changeStatus(20L, CapabilityInvocationStatus.APPROVED,
                CapabilityInvocationStatus.RUNNING, "evt-1", null, null);
    }

    private static CapabilityInvocationEntity invocation() {
        CapabilityInvocationEntity invocation = new CapabilityInvocationEntity();
        invocation.setId(20L);
        invocation.setCheckpointId(30L);
        invocation.setInteractionId(10L);
        invocation.setIdempotencyKey("evt-1");
        invocation.setStatus(CapabilityInvocationStatus.RUNNING);
        return invocation;
    }

    private static AgentScopeCheckpoint checkpoint() {
        return AgentScopeCheckpoint.builder()
                .version(AgentScopeCheckpoint.CURRENT_VERSION)
                .query("发送邮件").capabilityCode("mail_agent").applicationVersionId(30L)
                .userId(7L).conversationId(8L).assistantMessageId(9L)
                .runId("run-1").agentSessionId("run-1").agentId(11L).agentVersionId(12L)
                .maxModelCalls(3).maxToolCalls(2).maxTotalTokens(100)
                .maxFinalizationTokens(120)
                .modelCalls(1).toolCalls(1).tokenUsage(TokenUsage.empty()).lastSequenceNo(4L)
                .finalizationRequested(false).output("")
                .toolCallCounts(List.of())
                .authorizedToolCodes(List.of("send_mail"))
                .agentStateJson("{\"version\":1}")
                .pendingToolCall(new AgentScopeCheckpoint.ToolCallSnapshot("call-1", "send_mail", Map.of()))
                .toolConfigurationHash("mail-v1")
                .resourceScope("用户邮箱")
                .fingerprint("fingerprint")
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();
    }
}
