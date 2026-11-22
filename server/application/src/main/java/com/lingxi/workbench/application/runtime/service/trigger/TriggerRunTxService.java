package com.lingxi.workbench.application.runtime.service.trigger;

import com.lingxi.workbench.application.runtime.common.enums.TriggerFireStatus;
import com.lingxi.workbench.application.runtime.model.entity.TriggerEntity;
import com.lingxi.workbench.application.runtime.service.trigger.config.TriggerProperties;
import com.lingxi.workbench.application.runtime.repo.TriggerFireRepo;
import com.lingxi.workbench.application.runtime.repo.TriggerRepo;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionTxBO;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.notification.model.bo.RunResultNotificationBO;
import com.lingxi.workbench.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 定时触发器主动运行的终态协同。
 *
 * <p>主动运行复用 Assistant 消息的完成事务消息，在同一个本地事务内推进消息终态、
 * 触发记录终态与触发器最近运行信息；失败路径由生成任务所在实例直接调用并记录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerRunTxService {

    private static final int ERROR_MESSAGE_MAX_CHARACTERS = 256;

    private final TriggerRepo triggerRepo;
    private final TriggerFireRepo fireRepo;
    private final ConversationService conversationService;
    private final NotificationService notificationService;
    private final TriggerProperties properties;

    /**
     * 同一本地事务内完成 Assistant 消息终态与触发器侧记录。
     *
     * @param tx    完成事务参数（含触发器上下文）
     * @param event 事务事件，提供消息定位信息
     */
    @Transactional
    public void completeAssistantAndFire(AssistantCompletionTxBO tx, ConversationCompletedMessage event) {
        requireTriggerContext(tx);
        TriggerEntity trigger = triggerRepo.findById(tx.getTriggerId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "定时触发器不存在"));
        conversationService.completeAssistant(
                event.assistantMessageId(), tx.getContent(), tx.getTokenUsage(), tx.getMetadata());
        String runKey = String.valueOf(event.assistantMessageId());
        fireRepo.markSucceeded(tx.getFireId(), runKey);
        triggerRepo.recordRunResult(tx.getTriggerId(), runKey, TriggerFireStatus.SUCCEEDED.getCode(),
                OffsetDateTime.now(), true);
        notificationService.createRunResult(RunResultNotificationBO.builder()
                .userId(trigger.getUserId())
                .triggerId(trigger.getId())
                .conversationId(trigger.getConversationId())
                .messageId(event.assistantMessageId())
                .title(trigger.getName() + " 已完成")
                .summary(buildRunSummary(tx.getContent()))
                .build());
    }

    /**
     * 记录一次主动运行失败，连续失败达到阈值时自动暂停并通知。
     */
    @Transactional
    public void recordFailure(Long triggerId, Long fireId, Long userId, String errorMessage) {
        fireRepo.finish(fireId, TriggerFireStatus.FAILED.getCode(), null, truncate(errorMessage));
        triggerRepo.recordRunResult(triggerId, null, TriggerFireStatus.FAILED.getCode(), OffsetDateTime.now(), false);
        if (triggerRepo.autoPauseOnFailureThreshold(triggerId, properties.getAutoPauseFailures(), "连续失败自动暂停")) {
            log.warn("定时触发器连续失败达到阈值，已自动暂停。triggerId={}, threshold={}", triggerId, properties.getAutoPauseFailures());
            notificationService.createRunResult(RunResultNotificationBO.builder()
                    .userId(userId)
                    .triggerId(triggerId)
                    .conversationId(null)
                    .messageId(null)
                    .title("定时任务已自动暂停")
                    .summary("任务连续失败" + properties.getAutoPauseFailures() + "次，已停止调度；请检查任务目标后手动恢复")
                    .build());
        }
    }

    /**
     * 用户主动取消主动运行时收敛触发记录。
     */
    @Transactional
    public void recordCancelled(Long triggerId, Long fireId) {
        fireRepo.finish(fireId, TriggerFireStatus.CANCELLED.getCode(), null, "用户取消");
        triggerRepo.recordRunResult(triggerId, null, TriggerFireStatus.CANCELLED.getCode(), OffsetDateTime.now(), false);
    }

    /**
     * 对账收敛长时间无终态的触发记录，并推进失败计数与自动暂停判定。
     */
    @Transactional
    public void expireFire(Long fireId, Long triggerId, Long userId) {
        boolean finished = fireRepo.finish(fireId, TriggerFireStatus.EXPIRED.getCode(), null, "运行超时，由对账收敛");
        if (!finished) {
            return;
        }
        triggerRepo.recordRunResult(triggerId, null, TriggerFireStatus.EXPIRED.getCode(), OffsetDateTime.now(), false);
        if (triggerRepo.autoPauseOnFailureThreshold(triggerId, properties.getAutoPauseFailures(), "连续失败自动暂停")) {
            notificationService.createRunResult(RunResultNotificationBO.builder()
                    .userId(userId)
                    .triggerId(triggerId)
                    .conversationId(null)
                    .messageId(null)
                    .title("定时任务已自动暂停")
                    .summary("任务连续失败" + properties.getAutoPauseFailures() + "次，已停止调度；请检查任务目标后手动恢复")
                    .build());
        }
    }

    /** 运行结果摘要：截取回答开头内容，不含完整正文。 */
    private static String buildRunSummary(String content) {
        String answer = Objects.requireNonNullElse(content, "").replaceAll("\\s+", " ").trim();
        if (answer.isEmpty()) {
            return "任务执行完成，点击查看详情";
        }
        return answer.length() <= 120 ? answer : answer.substring(0, 120) + "…";
    }

    private static String truncate(String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return value.length() <= ERROR_MESSAGE_MAX_CHARACTERS ? value : value.substring(0, ERROR_MESSAGE_MAX_CHARACTERS);
    }

    /** 校验事务参数携带了触发器上下文。 */
    static void requireTriggerContext(AssistantCompletionTxBO tx) {
        if (Objects.isNull(tx.getTriggerId()) || Objects.isNull(tx.getFireId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "触发器完成事务缺少触发上下文");
        }
    }
}
