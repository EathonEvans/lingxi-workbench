package com.lingxi.workbench.application.runtime.service.trigger;

import com.lingxi.workbench.application.runtime.common.enums.TriggerFireStatus;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.model.entity.TriggerFireEntity;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.TriggerFireRepo;
import com.lingxi.workbench.application.runtime.service.trigger.config.TriggerProperties;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 定时触发器对账：收敛触发后长时间无终态的触发记录，防止自治运行失控。
 *
 * <p>会话消息的僵尸对账由 ConversationOpsService 负责；本对账以触发记录为依据，
 * 通过运行与消息的真实终态决定收敛目标，只有真正执行失败或失联才计入连续失败。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerFireReconciler {

    private final TriggerFireRepo fireRepo;
    private final ExecutionRunRepo runRepo;
    private final ConversationMessageRepo messageRepo;
    private final TriggerRunTxService triggerRunTxService;
    private final TriggerProperties properties;

    /** 周期对账；异常吞掉并记录告警，保证定时器不被单次失败中断。 */
    @Scheduled(fixedDelayString = "${lingxi.trigger.reconcile-delay:1m}")
    public void scheduledReconcile() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            OffsetDateTime staleBefore = OffsetDateTime.now().minus(properties.getStaleAfter());
            List<TriggerFireEntity> staleFires = fireRepo.findStale(staleBefore, properties.getBatchSize());
            for (TriggerFireEntity fire : staleFires) {
                try {
                    convergeStaleFire(fire);
                } catch (RuntimeException exception) {
                    log.warn("过期触发记录收敛失败，fireId={}", fire.getId(), exception);
                }
            }
        } catch (RuntimeException exception) {
            log.warn("触发记录对账失败，errorType={}", exception.getClass().getSimpleName());
        }
    }

    private void convergeStaleFire(TriggerFireEntity fire) {
        Optional<MessageStatus> messageStatus = findAssistantStatus(fire);
        if (messageStatus.isPresent()) {
            // 运行已产生真实终态：按事实收敛触发记录，不重复计失败。
            MessageStatus status = messageStatus.get();
            switch (status) {
                case COMPLETED -> fireRepo.finish(fire.getId(), TriggerFireStatus.SUCCEEDED.getCode(), null, null);
                case CANCELLED -> fireRepo.finish(fire.getId(), TriggerFireStatus.CANCELLED.getCode(), null, "用户取消");
                case FAILED, INTERRUPTED -> triggerRunTxService.expireFire(fire.getId(), fire.getTriggerId(),
                        fire.getUserId());
                case GENERATING, WAITING_HUMAN -> log.info("主动运行仍在进行，保持观察，fireId={}", fire.getId());
            }
            return;
        }
        log.warn("主动运行超时无终态，按过期收敛，fireId={}，triggerId={}", fire.getId(), fire.getTriggerId());
        triggerRunTxService.expireFire(fire.getId(), fire.getTriggerId(), fire.getUserId());
    }

    /**
     * 通过运行表反查主动运行的 Assistant 消息终态。
     *
     * <p>运行不存在（尚未启动即失联）或消息仍在生成态时返回空，由过期路径收敛。</p>
     */
    private Optional<MessageStatus> findAssistantStatus(TriggerFireEntity fire) {
        Optional<ExecutionRunEntity> run = runRepo.findByFireId(fire.getId());
        if (run.isEmpty()) {
            return Optional.empty();
        }
        return messageRepo.findById(run.get().getAssistantMessageId())
                .map(message -> {
                    MessageStatus status = message.getStatus();
                    return status == MessageStatus.GENERATING || status == MessageStatus.WAITING_HUMAN ? null : status;
                });
    }
}
