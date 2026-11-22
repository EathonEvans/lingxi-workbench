package com.lingxi.workbench.application.runtime.service.trigger;

import com.lingxi.workbench.application.runtime.common.enums.TriggerFireStatus;
import com.lingxi.workbench.application.runtime.common.enums.TriggerSkipReason;
import com.lingxi.workbench.application.runtime.model.entity.TriggerEntity;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.repo.TriggerFireRepo;
import com.lingxi.workbench.application.runtime.repo.TriggerRepo;
import com.lingxi.workbench.application.runtime.service.app.ApplicationVersionService;
import com.lingxi.workbench.application.runtime.service.execution.RedisLockExecutor;
import com.lingxi.workbench.application.runtime.service.trigger.config.TriggerProperties;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.framework.mq.MqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 定时触发器到期扫描：以 next_fire_at 为唯一调度依据，CAS 抢占保证多实例不重复触发。
 *
 * <p>到期触发器先在本地事务内抢占 next_fire_at 并落触发记录，再投递 MQ 消息执行；
 * 消息投递失败时触发记录收敛为过期，由日志与对账保持可观测。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerScanService {

    private static final String SCAN_LOCK_KEY = "lingxi:runtime:job:trigger-scan";
    private static final Duration SCAN_LOCK_WAIT = Duration.ZERO;
    private static final Duration SCAN_LOCK_LEASE = Duration.ofMinutes(2);

    private final TriggerRepo triggerRepo;
    private final TriggerFireRepo fireRepo;
    private final TriggerScanTxService scanTxService;
    private final TriggerProperties properties;
    private final RedisLockExecutor redisLockExecutor;
    private final MqProducer<Object> mqProducer;
    private final InteractionSessionRepo interactionSessionRepo;
    private final ConversationMessageRepo conversationMessageRepo;
    private final ApplicationVersionService appVersionService;

    /**
     * 周期扫描到期触发器。异常吞掉并记录告警，保证定时器不被单次失败中断。
     */
    @Scheduled(fixedDelayString = "${lingxi.trigger.scan-delay:30s}", initialDelayString = "${lingxi.trigger.scan-initial-delay:15s}")
    public void scheduledScan() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            Optional<Long> scanned = redisLockExecutor.tryExecute(SCAN_LOCK_KEY, SCAN_LOCK_WAIT, SCAN_LOCK_LEASE,
                    this::scanDueTriggers);
            if (scanned.isEmpty()) {
                log.debug("其他实例正在执行触发器到期扫描");
            }
        } catch (RuntimeException exception) {
            log.warn("触发器到期扫描失败，errorType={}", exception.getClass().getSimpleName());
        }
    }

    private Long scanDueTriggers() {
        OffsetDateTime now = OffsetDateTime.now();
        List<TriggerEntity> dueTriggers = triggerRepo.findDue(now, properties.getBatchSize());
        for (TriggerEntity trigger : dueTriggers) {
            try {
                fireDueTrigger(trigger, now);
            } catch (RuntimeException exception) {
                log.warn("到期触发处理失败，triggerId={}，errorType={}", trigger.getId(), exception.getClass().getSimpleName());
            }
        }
        return (long) dueTriggers.size();
    }

    private void fireDueTrigger(TriggerEntity trigger, OffsetDateTime now) {
        ZonedDateTime rawPlanned = TriggerScheduleCalculator.nextFire(trigger.getCronExpr(), trigger.getTimezone(),
                trigger.getNextFireAt().toZonedDateTime());
        ZonedDateTime planned = TriggerScheduleCalculator.shiftIfQuietHours(rawPlanned, trigger.getQuietHours());
        OffsetDateTime nextFireAt = planned.toOffsetDateTime();

        // 落点被静默顺延：记录跳过事实，本周期不执行，下一次触发时间已推进。
        if (!planned.toInstant().equals(rawPlanned.toInstant())) {
            scanTxService.recordSkipped(trigger, nextFireAt, now, TriggerFireStatus.SKIPPED,
                    TriggerSkipReason.QUIET_HOURS.getCode(), null);
            return;
        }

        Optional<TriggerSkipReason> skipReason = evaluateSkip(trigger);
        if (skipReason.isPresent()) {
            scanTxService.recordSkipped(trigger, nextFireAt, now, TriggerFireStatus.SKIPPED,
                    skipReason.get().getCode(), null);
            log.info("定时触发器到期但被跳过，triggerId={}，reason={}", trigger.getId(), skipReason.get().getCode());
            return;
        }

        Long fireId = scanTxService.claimAndRecordFire(trigger, nextFireAt, now);
        if (Objects.nonNull(fireId)) {
            dispatchFire(fireId, trigger.getId());
        }
    }

    /** 到期时刻的准入评估，全部通过才投递执行。 */
    private Optional<TriggerSkipReason> evaluateSkip(TriggerEntity trigger) {
        long firedToday = triggerRepo.countFiredSince(trigger.getUserId(),
                OffsetDateTime.of(LocalDate.now().atStartOfDay(), OffsetDateTime.now().getOffset()));
        if (firedToday >= properties.getDailyRunLimitPerUser()) {
            return Optional.of(TriggerSkipReason.DAILY_QUOTA);
        }
        try {
            appVersionService.loadRuntimeAppVersion(trigger.getApplicationId(), trigger.getApplicationVersionId());
        } catch (RuntimeException exception) {
            return Optional.of(TriggerSkipReason.VERSION_UNAVAILABLE);
        }
        if (interactionSessionRepo.findAnyPending(trigger.getConversationId(), trigger.getUserId(),
                trigger.getApplicationVersionId()).isPresent()) {
            return Optional.of(TriggerSkipReason.INTERACTION_PENDING);
        }
        if (conversationMessageRepo.existsGenerating(trigger.getConversationId())) {
            return Optional.of(TriggerSkipReason.CONVERSATION_BUSY);
        }
        return Optional.empty();
    }

    private void dispatchFire(Long fireId, Long triggerId) {
        try {
            mqProducer.send(TriggerMqConstants.TRIGGER_FIRE_TOPIC, "trigger-fire:" + fireId,
                    new TriggerFireMessage(fireId, triggerId));
        } catch (RuntimeException exception) {
            // 投递失败收敛触发记录为过期；触发记录仍在，可由日志定位，不计入连续失败。
            fireRepo.finish(fireId, TriggerFireStatus.EXPIRED.getCode(), null, "触发消息投递失败");
            log.warn("到期触发消息投递失败，fireId={}，triggerId={}", fireId, triggerId, exception);
        }
    }
}
