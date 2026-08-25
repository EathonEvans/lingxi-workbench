package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.execution.RedisLockExecutor;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HitlExpiryReconcilerTest {

    @Test
    void shouldOnlyScanExpiredActionApprovals() {
        InteractionSessionRepo interactionRepo = mock(InteractionSessionRepo.class);
        HitlDecisionTxService decisionTxService = mock(HitlDecisionTxService.class);
        RedisLockExecutor redisLockExecutor = passThroughLockExecutor();
        InteractionSessionEntity approval = new InteractionSessionEntity();
        approval.setId(10L);
        when(interactionRepo.findExpiredPending(
                eq(InteractionSessionType.ACTION_APPROVAL), any(OffsetDateTime.class), eq(100)))
                .thenReturn(List.of(approval));

        new HitlExpiryReconciler(interactionRepo, decisionTxService, redisLockExecutor).reconcile();

        verify(interactionRepo).findExpiredPending(
                eq(InteractionSessionType.ACTION_APPROVAL), any(OffsetDateTime.class), eq(100));
        verify(decisionTxService).expire(approval);
    }

    private static RedisLockExecutor passThroughLockExecutor() {
        RedisLockExecutor executor = mock(RedisLockExecutor.class);
        when(executor.tryExecute(any(String.class), any(), any(), any())).thenAnswer(invocation ->
                Optional.of(((Supplier<?>) invocation.getArgument(3)).get()));
        return executor;
    }
}
