package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionDecision;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.HitlDecisionTxBO;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.model.message.HitlResumeMessage;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HitlDecisionTxServiceTest {

    private final InteractionSessionRepo interactionRepo = mock(InteractionSessionRepo.class);
    private final CapabilityInvocationRepo invocationRepo = mock(CapabilityInvocationRepo.class);
    private final ExecutionCheckpointRepo checkpointRepo = mock(ExecutionCheckpointRepo.class);
    private final ExecutionRunRepo runRepo = mock(ExecutionRunRepo.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private HitlDecisionTxService service;

    @BeforeEach
    void setUp() {
        service = new HitlDecisionTxService(
                interactionRepo, invocationRepo, checkpointRepo, runRepo, conversationService);
    }

    @Test
    void shouldApproveExactInvocationAndMoveSameRunToResuming() {
        InteractionSessionEntity interaction = interaction(InteractionSessionStatus.PENDING);
        CapabilityInvocationEntity invocation = invocation();
        when(interactionRepo.findOwned(10L, 7L)).thenReturn(Optional.of(interaction));
        when(checkpointRepo.findById(30L)).thenReturn(Optional.of(checkpoint()));
        when(interactionRepo.decide(eq(10L), eq(7L), eq(0), eq(InteractionSessionStatus.RESOLVED),
                eq(InteractionDecision.APPROVE), eq(7L), eq("evt-1"), any(OffsetDateTime.class))).thenReturn(true);
        when(invocationRepo.findByInteractionId(10L)).thenReturn(Optional.of(invocation));
        when(invocationRepo.changeStatus(20L, CapabilityInvocationStatus.PENDING_APPROVAL,
                CapabilityInvocationStatus.APPROVED, "evt-1", null, null)).thenReturn(true);
        when(runRepo.changeStatus("run-1", ExecutionRunStatus.WAITING_HUMAN,
                ExecutionRunStatus.RESUMING, "HITL_APPROVED", null)).thenReturn(true);

        assertDoesNotThrow(() -> service.decide(tx(InteractionDecision.APPROVE)));

        verify(runRepo).changeStatus("run-1", ExecutionRunStatus.WAITING_HUMAN,
                ExecutionRunStatus.RESUMING, "HITL_APPROVED", null);
        verify(conversationService, never()).cancelWaiting(anyLong(), any(), any());
    }

    @Test
    void shouldReturnIdempotentlyForSameCommittedDecision() {
        InteractionSessionEntity interaction = interaction(InteractionSessionStatus.RESOLVED);
        interaction.setIdempotencyKey("evt-1");
        when(interactionRepo.findOwned(10L, 7L)).thenReturn(Optional.of(interaction));

        assertDoesNotThrow(() -> service.decide(tx(InteractionDecision.APPROVE)));

        verify(interactionRepo, never()).decide(anyLong(), anyLong(), eq(0), any(), any(), anyLong(), any(), any());
        verify(invocationRepo, never()).findByInteractionId(anyLong());
    }

    @Test
    void shouldRejectApprovalWhenCheckpointFingerprintChanged() {
        InteractionSessionEntity interaction = interaction(InteractionSessionStatus.PENDING);
        ExecutionCheckpointEntity checkpoint = checkpoint();
        checkpoint.setChecksum("changed");
        when(interactionRepo.findOwned(10L, 7L)).thenReturn(Optional.of(interaction));
        when(checkpointRepo.findById(30L)).thenReturn(Optional.of(checkpoint));

        assertThrows(BusinessException.class, () -> service.decide(tx(InteractionDecision.APPROVE)));

        verify(interactionRepo, never()).decide(anyLong(), anyLong(), eq(0), any(), any(), anyLong(), any(), any());
        verify(invocationRepo, never()).findByInteractionId(anyLong());
    }

    @Test
    void shouldExpireWaitingRunInsteadOfResumingExpiredApproval() {
        InteractionSessionEntity interaction = interaction(InteractionSessionStatus.PENDING);
        interaction.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(interactionRepo.findOwned(10L, 7L)).thenReturn(Optional.of(interaction));
        when(interactionRepo.expire(10L, 0)).thenReturn(true);
        when(invocationRepo.findByInteractionId(10L)).thenReturn(Optional.of(invocation()));
        when(invocationRepo.changeStatus(20L, CapabilityInvocationStatus.PENDING_APPROVAL,
                CapabilityInvocationStatus.EXPIRED, null, null, "APPROVAL_EXPIRED")).thenReturn(true);
        when(checkpointRepo.expire(30L)).thenReturn(true);
        when(runRepo.changeStatus(eq("run-1"), eq(ExecutionRunStatus.WAITING_HUMAN),
                eq(ExecutionRunStatus.EXPIRED), eq("HITL_EXPIRED"), any(OffsetDateTime.class))).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.decide(tx(InteractionDecision.APPROVE)));

        verify(checkpointRepo).expire(30L);
        verify(conversationService).cancelWaiting(40L, "APPROVAL_EXPIRED", "审批已过期，本轮任务已结束");
    }

    @Test
    void shouldCancelAllWaitingStateForOwnedAssistantMessage() {
        InteractionSessionEntity interaction = interaction(InteractionSessionStatus.PENDING);
        CapabilityInvocationEntity invocation = invocation();
        when(interactionRepo.findPendingOwnedByAssistant(40L, 7L, InteractionSessionType.ACTION_APPROVAL))
                .thenReturn(Optional.of(interaction));
        when(interactionRepo.findOwned(10L, 7L)).thenReturn(Optional.of(interaction));
        when(interactionRepo.decide(eq(10L), eq(7L), eq(0), eq(InteractionSessionStatus.CANCELLED),
                eq(InteractionDecision.CANCEL), eq(7L), eq("cancel-10-0"), any(OffsetDateTime.class))).thenReturn(true);
        when(invocationRepo.findByInteractionId(10L)).thenReturn(Optional.of(invocation));
        when(invocationRepo.changeStatus(20L, CapabilityInvocationStatus.PENDING_APPROVAL,
                CapabilityInvocationStatus.CANCELLED, "cancel-10-0", null, "USER_CANCELLED")).thenReturn(true);
        when(checkpointRepo.supersede(30L)).thenReturn(true);
        when(runRepo.changeStatus(eq("run-1"), eq(ExecutionRunStatus.WAITING_HUMAN),
                eq(ExecutionRunStatus.CANCELLED), eq("USER_CANCELLED"), any(OffsetDateTime.class))).thenReturn(true);

        assertDoesNotThrow(() -> service.cancel(40L, 7L));

        verify(conversationService).cancelWaiting(40L, "USER_CANCELLED", "你已取消本轮待确认操作");
    }

    private static InteractionSessionEntity interaction(InteractionSessionStatus status) {
        InteractionSessionEntity interaction = new InteractionSessionEntity();
        interaction.setId(10L);
        interaction.setUserId(7L);
        interaction.setRunId("run-1");
        interaction.setCheckpointId(30L);
        interaction.setAssistantMessageId(40L);
        interaction.setInteractionType(InteractionSessionType.ACTION_APPROVAL);
        interaction.setStatus(status);
        interaction.setRevision(0);
        interaction.setApprovalFingerprint("fingerprint");
        interaction.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        return interaction;
    }

    private static CapabilityInvocationEntity invocation() {
        CapabilityInvocationEntity invocation = new CapabilityInvocationEntity();
        invocation.setId(20L);
        invocation.setStatus(CapabilityInvocationStatus.PENDING_APPROVAL);
        return invocation;
    }

    private static ExecutionCheckpointEntity checkpoint() {
        ExecutionCheckpointEntity checkpoint = new ExecutionCheckpointEntity();
        checkpoint.setId(30L);
        checkpoint.setRunId("run-1");
        checkpoint.setAssistantMessageId(40L);
        checkpoint.setStatus(ExecutionCheckpointStatus.ACTIVE);
        checkpoint.setChecksum("fingerprint");
        checkpoint.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        return checkpoint;
    }

    private static HitlDecisionTxBO tx(InteractionDecision decision) {
        HitlResumeMessage event = HitlResumeMessage.builder()
                .eventId("evt-1")
                .interactionId(10L)
                .build();
        return HitlDecisionTxBO.builder()
                .event(event)
                .userId(7L)
                .decision(decision)
                .expectedRevision(0)
                .fingerprint("fingerprint")
                .build();
    }
}
