package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** 定时收敛无人处理的审批，避免运行和会话永久停留在等待状态。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HitlExpiryReconciler {

    private static final int BATCH_SIZE = 100;

    private final InteractionSessionRepo interactionRepo;
    private final HitlDecisionTxService decisionTxService;

    @Scheduled(fixedDelayString = "${lingxi.hitl.expiry-scan-delay-ms:60000}")
    public void reconcile() {
        for (InteractionSessionEntity interaction : interactionRepo.findExpiredPending(
                InteractionSessionType.ACTION_APPROVAL, OffsetDateTime.now(), BATCH_SIZE)) {
            try {
                decisionTxService.expire(interaction);
            } catch (RuntimeException exception) {
                log.warn("收敛过期HITL交互失败，interactionId={}", interaction.getId(), exception);
            }
        }
    }
}
