package com.lingxi.workbench.admin.evaluation.schedule;

import com.lingxi.workbench.admin.evaluation.repo.EvaluationRunRepo;
import com.lingxi.workbench.admin.evaluation.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** 补偿 Admin 重启或工作线程异常中断后未完成的评测任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationRunRecovery {

    private static final int RECOVERY_BATCH_SIZE = 20;
    private static final int STALE_MINUTES = 10;

    private final EvaluationRunRepo runRepo;
    private final EvaluationService evaluationService;

    @Scheduled(fixedDelayString = "${lingxi.evaluation.recovery-interval-millis:30000}")
    public void recover() {
        int resetCount = runRepo.resetStale(OffsetDateTime.now().minusMinutes(STALE_MINUTES));
        if (resetCount > 0) {
            log.warn("已重置失去执行心跳的评测任务，count={}", resetCount);
        }
        for (Long runId : runRepo.findPendingIds(RECOVERY_BATCH_SIZE)) {
            evaluationService.recoverRun(runId);
        }
    }
}
