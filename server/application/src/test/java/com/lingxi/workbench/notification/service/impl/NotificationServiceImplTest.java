package com.lingxi.workbench.notification.service.impl;

import com.lingxi.workbench.framework.convention.PageQuery;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.notification.model.bo.HitlPendingNotificationBO;
import com.lingxi.workbench.notification.model.bo.NotificationPayloadBO;
import com.lingxi.workbench.notification.model.converter.NotificationConverter;
import com.lingxi.workbench.notification.model.entity.NotificationEntity;
import com.lingxi.workbench.notification.model.vo.NotificationVO;
import com.lingxi.workbench.notification.repo.NotificationRepo;
import com.lingxi.workbench.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private final NotificationRepo notificationRepo = mock(NotificationRepo.class);
    private final NotificationConverter notificationConverter = mock(NotificationConverter.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(notificationRepo, notificationConverter);

    @BeforeEach
    void setUp() {
        when(notificationConverter.toVO(any(NotificationEntity.class))).thenReturn(new NotificationVO());
        when(notificationRepo.findPage(any(), any())).thenReturn(new PageResult<>(List.of(new NotificationEntity()), 1, 10, 1));
    }

    @Test
    void shouldCreateHitlPendingNotificationWithDedupKeyAndPayload() {
        HitlPendingNotificationBO bo = HitlPendingNotificationBO.builder()
                .userId(7L)
                .conversationId(10L)
                .assistantMessageId(12L)
                .interactionId(91L)
                .title("确认发送邮件")
                .summary("将向外部邮箱发送内容")
                .build();

        service.createHitlPending(bo);

        verify(notificationRepo).insertWithDedupIfAbsent(org.mockito.ArgumentMatchers.argThat(entity ->
                entity.getUserId() == 7L
                        && entity.getType().getCode().equals("HITL_PENDING")
                        && "hitl:91".equals(entity.getDedupKey())
                        && entity.getPayload().getConversationId() == 10L
                        && entity.getPayload().getMessageId() == 12L
                        && entity.getPayload().getInteractionId() == 91L));
    }

    @Test
    void shouldSkipSilentlyWhenDedupKeyAlreadyExists() {
        when(notificationRepo.insertWithDedupIfAbsent(any())).thenReturn(false);
        HitlPendingNotificationBO bo = HitlPendingNotificationBO.builder()
                .userId(7L).conversationId(10L).assistantMessageId(12L).interactionId(91L)
                .title("确认发送邮件").summary("摘要")
                .build();

        assertDoesNotThrow(() -> service.createHitlPending(bo));
    }

    @Test
    void shouldMarkInteractionNotificationReadByDedupKey() {
        service.markInteractionNotificationsRead(7L, 91L);

        verify(notificationRepo).markReadByDedupKey(7L, "hitl:91");
    }

    @Test
    void shouldIgnoreInvalidMarkReadArguments() {
        service.markInteractionNotificationsRead(null, 91L);
        service.markInteractionNotificationsRead(7L, null);

        verify(notificationRepo, never()).markReadByDedupKey(any(), any());
    }

    @Test
    void shouldPageAndCountUnreadForOwner() {
        PageResult<NotificationVO> page = service.page(7L, new PageQuery());

        assertEquals(1, page.records().size());
        service.unreadCount(7L);
        verify(notificationRepo).countUnread(7L);
    }

    @Test
    void shouldDelegateMarkReadOperations() {
        service.markRead(7L, List.of(1L, 2L));
        service.markAllRead(7L);

        verify(notificationRepo).markReadBatch(eq(7L), any());
        verify(notificationRepo).markAllRead(7L);
        assertTrue(true);
    }
}
