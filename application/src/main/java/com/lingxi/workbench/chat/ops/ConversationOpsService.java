package com.lingxi.workbench.chat.ops;

import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.ops.common.enums.ConversationDlqGroup;
import com.lingxi.workbench.chat.ops.config.ConversationOpsProperties;
import com.lingxi.workbench.chat.ops.model.bo.MqHealthBO;
import com.lingxi.workbench.chat.ops.model.vo.ConversationDlqMessageVO;
import com.lingxi.workbench.chat.ops.model.vo.ConversationOpsOverviewVO;
import com.lingxi.workbench.chat.ops.model.vo.ConversationReconcileVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.chat.event.ConversationEventPublisher;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** 会话事务消息健康检查、对账、死信查询与人工重放入口。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationOpsService {

    private static final String COMPLETED_EVENT_PREFIX = "conversation-completed:";

    private final ConversationMessageRepo messageRepo;
    private final ChatStreamService chatStreamService;
    private final ConversationEventPublisher eventPublisher;
    private final ConversationMqInspector mqInspector;
    private final ConversationOpsMetrics metrics;
    private final ConversationOpsProperties properties;
    private final AtomicBoolean reconciling = new AtomicBoolean();
    /** 不对服务启动前的历史完成消息做记忆重建。 */
    private final OffsetDateTime serviceStartedAt = OffsetDateTime.now();

    public ConversationOpsOverviewVO overview() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime staleBefore = now.minus(properties.getStaleAfter());
        OffsetDateTime memoryBefore = now.minus(properties.getMemoryDelay());
        MqHealthBO health = mqInspector.health();
        metrics.recordMqHealth(health.available());
        Map<ConversationDlqGroup, Long> counts = health.available()
                ? mqInspector.dlqCounts()
                : Arrays.stream(ConversationDlqGroup.values())
                        .collect(Collectors.toMap(group -> group, group -> 0L));
        Map<String, Long> groupCounts = counts.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getCode(), Map.Entry::getValue));
        long dlqTotal = counts.values().stream().mapToLong(Long::longValue).sum();
        metrics.updateDlqCounts(counts);
        return ConversationOpsOverviewVO.builder()
                .mqAvailable(health.available())
                .mqMessage(health.message())
                .checkedAt(health.checkedAt())
                .staleGenerating(messageRepo.countStaleGenerating(staleBefore))
                .pendingMemoryReconcile(messageRepo.countMemoryReconcileCandidates(
                        serviceStartedAt, memoryBefore))
                .dlqTotal(dlqTotal)
                .dlqByGroup(groupCounts)
                .build();
    }

    public List<ConversationDlqMessageVO> dlq(int limit) {
        return mqInspector.latest(limit);
    }

    public void replay(ConversationDlqGroup group, String msgId) {
        ConversationCompletedMessage event = mqInspector.requireEvent(group, msgId);
        requireCompletedEvent(event);
        eventPublisher.replay(event);
        metrics.recordManualReplay();
        log.info("人工重放会话记忆事件，group={}，eventId={}，assistantMessageId={}",
                group.getCode(), event.eventId(), event.assistantMessageId());
    }

    public ConversationReconcileVO reconcile() {
        if (!reconciling.compareAndSet(false, true)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "会话事件对账正在执行");
        }
        try {
            OffsetDateTime now = OffsetDateTime.now();
            int interrupted = chatStreamService.reconcileStaleGenerations(
                    now.minus(properties.getStaleAfter()), properties.getBatchSize());
            int republished = reconcileMemoryEvents(now.minus(properties.getMemoryDelay()));
            metrics.recordStaleRepaired(interrupted);
            metrics.recordMemoryRepublished(republished);
            return new ConversationReconcileVO(interrupted, republished);
        } finally {
            reconciling.set(false);
        }
    }

    private int reconcileMemoryEvents(OffsetDateTime completedBefore) {
        int republished = 0;
        List<ConversationMessageEntity> candidates = messageRepo.findMemoryReconcileCandidates(
                serviceStartedAt, completedBefore, properties.getBatchSize());
        for (ConversationMessageEntity message : candidates) {
            if (Objects.isNull(message.getApplicationVersionId())) {
                continue;
            }
            ConversationCompletedMessage event = new ConversationCompletedMessage(
                    COMPLETED_EVENT_PREFIX + message.getId(), message.getConversationId(),
                    message.getId(), message.getApplicationVersionId());
            eventPublisher.replay(event);
            if (messageRepo.markMemoryReconciled(message.getId(), OffsetDateTime.now())) {
                republished++;
            }
        }
        return republished;
    }

    private void requireCompletedEvent(ConversationCompletedMessage event) {
        messageRepo.findCompletedAssistant(
                        event.conversationId(), event.assistantMessageId(), event.appVersionId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "死信对应的Assistant消息不再满足重放条件"));
    }

    @Scheduled(fixedDelayString = "${lingxi.chat.ops.reconcile-delay:1m}")
    void scheduledReconcile() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            ConversationReconcileVO result = reconcile();
            if (result.interruptedCount() > 0 || result.republishedCount() > 0) {
                log.info("会话事件定时对账完成，interrupted={}，republished={}",
                        result.interruptedCount(), result.republishedCount());
            }
        } catch (RuntimeException exception) {
            metrics.recordReconcileFailure();
            log.warn("会话事件定时对账失败，errorType={}", exception.getClass().getSimpleName());
        }
    }

    /** 启动检查只更新可观测状态，不因外部MQ短暂不可用阻断用户登录和只读接口。 */
    @EventListener(ApplicationReadyEvent.class)
    void checkMqOnStartup() {
        MqHealthBO health = mqInspector.health();
        metrics.recordMqHealth(health.available());
        if (!health.available()) {
            log.warn("会话事务消息启动检查未通过，reason={}", health.message());
        }
    }
}
