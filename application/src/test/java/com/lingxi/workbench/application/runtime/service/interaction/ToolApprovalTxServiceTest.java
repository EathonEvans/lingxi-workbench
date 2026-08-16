package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolApprovalTxServiceTest {

    private final InteractionSessionRepo interactionRepo = mock(InteractionSessionRepo.class);
    private final ExecutionCheckpointRepo checkpointRepo = mock(ExecutionCheckpointRepo.class);
    private final CapabilityInvocationRepo invocationRepo = mock(CapabilityInvocationRepo.class);
    private ToolApprovalTxService service;

    @BeforeEach
    void setUp() {
        service = new ToolApprovalTxService(interactionRepo, checkpointRepo, invocationRepo,
                mock(ExecutionPersistenceService.class), new ToolApprovalFingerprint());
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

    private static CapabilityInvocationEntity invocation() {
        CapabilityInvocationEntity invocation = new CapabilityInvocationEntity();
        invocation.setId(20L);
        invocation.setCheckpointId(30L);
        invocation.setInteractionId(10L);
        invocation.setIdempotencyKey("evt-1");
        invocation.setStatus(CapabilityInvocationStatus.RUNNING);
        return invocation;
    }
}
