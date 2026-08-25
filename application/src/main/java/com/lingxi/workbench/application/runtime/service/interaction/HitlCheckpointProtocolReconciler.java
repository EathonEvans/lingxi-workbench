package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.execution.RedisLockExecutor;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** 发布新检查点协议后，收敛不能由当前运行时恢复的存量人工审批。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HitlCheckpointProtocolReconciler {

    private static final int BATCH_SIZE = 100;
    private static final String PROTOCOL_SCAN_LOCK = "lingxi:runtime:job:hitl-checkpoint-protocol-scan";
    private static final Duration LOCK_LEASE = Duration.ofSeconds(30);

    private final ExecutionCheckpointRepo checkpointRepo;
    private final InteractionSessionRepo interactionRepo;
    private final HitlDecisionTxService decisionTxService;
    private final RedisLockExecutor redisLockExecutor;

    @Scheduled(initialDelayString = "${lingxi.hitl.checkpoint-protocol-initial-delay-ms:1000}",
            fixedDelayString = "${lingxi.hitl.checkpoint-protocol-scan-delay-ms:60000}")
    public void reconcile() {
        boolean executed = redisLockExecutor.tryExecute(PROTOCOL_SCAN_LOCK, Duration.ZERO, LOCK_LEASE, () -> {
            reconcileOutdatedCheckpoints();
            return Boolean.TRUE;
        }).isPresent();
        if (!executed) {
            log.debug("其他实例正在收敛旧HITL检查点协议");
        }
    }

    private void reconcileOutdatedCheckpoints() {
        for (ExecutionCheckpointEntity checkpoint : checkpointRepo.findActiveBeforeVersion(
                AgentScopeCheckpoint.CURRENT_VERSION, BATCH_SIZE)) {
            Optional<InteractionSessionEntity> interaction = interactionRepo.findPendingByCheckpointId(checkpoint.getId());
            if (interaction.isEmpty()) {
                log.warn("旧HITL检查点缺少待处理审批，checkpointId={}, runId={}", checkpoint.getId(), checkpoint.getRunId());
                continue;
            }
            try {
                decisionTxService.supersedeCheckpointProtocol(interaction.get());
            } catch (RuntimeException exception) {
                log.warn("收敛旧HITL检查点协议失败，checkpointId={}, interactionId={}", checkpoint.getId(),
                        interaction.get().getId(), exception);
            }
        }
    }
}
