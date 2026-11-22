package com.lingxi.workbench.admin.evaluation.schedule;

import com.lingxi.workbench.admin.evaluation.repo.EvaluationRunRepo;
import com.lingxi.workbench.admin.evaluation.service.EvaluationService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationRunRecoveryTest {

    @Test
    void shouldResetStaleExecutionAndReschedulePendingRuns() {
        EvaluationRunRepo runRepo = mock(EvaluationRunRepo.class);
        EvaluationService evaluationService = mock(EvaluationService.class);
        when(runRepo.findPendingIds(20)).thenReturn(List.of(11L, 12L));
        EvaluationRunRecovery recovery = new EvaluationRunRecovery(runRepo, evaluationService);

        recovery.recover();

        verify(runRepo).resetStale(any(OffsetDateTime.class));
        verify(evaluationService).recoverRun(11L);
        verify(evaluationService).recoverRun(12L);
    }
}
