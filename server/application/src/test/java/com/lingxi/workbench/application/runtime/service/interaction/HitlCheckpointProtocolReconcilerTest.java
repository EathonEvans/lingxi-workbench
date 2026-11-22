package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.execution.RedisLockExecutor;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HitlCheckpointProtocolReconcilerTest {

    @Test
    void shouldSupersedePendingApprovalForOutdatedCheckpoint() {
        ExecutionCheckpointRepo checkpointRepo = mock(ExecutionCheckpointRepo.class);
        InteractionSessionRepo interactionRepo = mock(InteractionSessionRepo.class);
        HitlDecisionTxService decisionTxService = mock(HitlDecisionTxService.class);
        RedisLockExecutor redisLockExecutor = passThroughLockExecutor();
        ExecutionCheckpointEntity checkpoint = new ExecutionCheckpointEntity();
        checkpoint.setId(30L);
        checkpoint.setRunId("run-1");
        InteractionSessionEntity interaction = new InteractionSessionEntity();
        interaction.setId(10L);
        when(checkpointRepo.findActiveBeforeVersion(AgentScopeCheckpoint.CURRENT_VERSION, 100))
                .thenReturn(List.of(checkpoint));
        when(interactionRepo.findPendingByCheckpointId(30L)).thenReturn(Optional.of(interaction));

        new HitlCheckpointProtocolReconciler(checkpointRepo, interactionRepo, decisionTxService, redisLockExecutor)
                .reconcile();

        verify(checkpointRepo).findActiveBeforeVersion(AgentScopeCheckpoint.CURRENT_VERSION, 100);
        verify(interactionRepo).findPendingByCheckpointId(30L);
        verify(decisionTxService).supersedeCheckpointProtocol(interaction);
    }

    private static RedisLockExecutor passThroughLockExecutor() {
        RedisLockExecutor executor = mock(RedisLockExecutor.class);
        when(executor.tryExecute(any(String.class), any(), any(), any())).thenAnswer(invocation ->
                Optional.of(((Supplier<?>) invocation.getArgument(3)).get()));
        return executor;
    }
}
