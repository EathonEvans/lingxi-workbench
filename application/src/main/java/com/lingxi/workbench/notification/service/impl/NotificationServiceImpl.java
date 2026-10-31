package com.lingxi.workbench.notification.service.impl;

import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.notification.common.enums.NotificationType;
import com.lingxi.workbench.notification.model.bo.HitlPendingNotificationBO;
import com.lingxi.workbench.notification.model.bo.RunResultNotificationBO;
import com.lingxi.workbench.notification.model.bo.NotificationPayloadBO;
import com.lingxi.workbench.notification.model.converter.NotificationConverter;
import com.lingxi.workbench.notification.model.entity.NotificationEntity;
import com.lingxi.workbench.notification.model.vo.NotificationVO;
import com.lingxi.workbench.notification.repo.NotificationRepo;
import com.lingxi.workbench.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 站内通知服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final String HITL_DEDUP_PREFIX = "hitl:";

    private final NotificationRepo notificationRepo;
    private final NotificationConverter notificationConverter;

    @Override
    @Transactional
    public void createHitlPending(HitlPendingNotificationBO bo) {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(bo.userId());
        entity.setType(NotificationType.HITL_PENDING);
        entity.setTitle(bo.title());
        entity.setContent(bo.summary());
        entity.setPayload(NotificationPayloadBO.builder()
                .conversationId(bo.conversationId())
                .messageId(bo.assistantMessageId())
                .interactionId(bo.interactionId())
                .build());
        entity.setDedupKey(HITL_DEDUP_PREFIX + bo.interactionId());
        boolean created = notificationRepo.insertWithDedupIfAbsent(entity);
        if (!created) {
            log.info("HITL待确认通知已存在，跳过重复创建。userId={}, interactionId={}", bo.userId(), bo.interactionId());
        }
    }

    @Override
    @Transactional
    public void createRunResult(RunResultNotificationBO bo) {
        NotificationEntity entity = new NotificationEntity();
        entity.setUserId(bo.userId());
        entity.setType(NotificationType.RUN_RESULT);
        entity.setTitle(bo.title());
        entity.setContent(bo.summary());
        entity.setPayload(NotificationPayloadBO.builder()
                .conversationId(bo.conversationId())
                .messageId(bo.messageId())
                .triggerId(bo.triggerId())
                .build());
        notificationRepo.insertWithDedupIfAbsent(entity);
    }

    @Override
    @Transactional
    public void markInteractionNotificationsRead(Long userId, Long interactionId) {
        if (Objects.isNull(userId) || Objects.isNull(interactionId)) {
            return;
        }
        notificationRepo.markReadByDedupKey(userId, HITL_DEDUP_PREFIX + interactionId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationVO> page(Long userId, PageQuery query) {
        return notificationRepo.findPage(userId, query).map(notificationConverter::toVO);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepo.countUnread(userId);
    }

    @Override
    @Transactional
    public void markRead(Long userId, List<Long> ids) {
        notificationRepo.markReadBatch(userId, ids);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepo.markAllRead(userId);
    }
}
