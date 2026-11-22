package com.lingxi.workbench.application.runtime.service.trigger;

import com.lingxi.workbench.application.runtime.common.enums.TriggerFireStatus;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionTxBO;
import com.lingxi.workbench.application.runtime.model.entity.TriggerEntity;
import com.lingxi.workbench.application.runtime.repo.TriggerFireRepo;
import com.lingxi.workbench.application.runtime.repo.TriggerRepo;
import com.lingxi.workbench.application.runtime.service.trigger.config.TriggerProperties;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionMetaBO;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.notification.model.bo.RunResultNotificationBO;
import com.lingxi.workbench.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriggerRunTxServiceTest {

    private final TriggerRepo triggerRepo = mock(TriggerRepo.class);
    private final TriggerFireRepo fireRepo = mock(TriggerFireRepo.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final TriggerProperties properties = new TriggerProperties();
    private final TriggerRunTxService service = new TriggerRunTxService(
            triggerRepo, fireRepo, conversationService, notificationService, properties);

    private final ConversationCompletedMessage event =
            new ConversationCompletedMessage("conversation-completed:12", 10L, 12L, 30L);

    @BeforeEach
    void setUp() {
        when(triggerRepo.findById(5L)).thenReturn(Optional.of(trigger()));
    }

    @Test
    void shouldCompleteAssistantAndConvergeFireAndTriggerInSameUseCase() {
        when(fireRepo.markSucceeded(eq(7L), anyString())).thenReturn(true);
        AssistantCompletionTxBO tx = AssistantCompletionTxBO.builder()
                .event(event)
                .content("今天的工单汇总如下")
                .tokenUsage(TokenUsage.of(10, 5, 15))
                .metadata(AssistantCompletionMetaBO.completed(40L))
                .triggerId(5L)
                .fireId(7L)
                .build();

        service.completeAssistantAndFire(tx, event);

        verify(conversationService).completeAssistant(12L, "今天的工单汇总如下",
                TokenUsage.of(10, 5, 15), AssistantCompletionMetaBO.completed(40L));
        verify(fireRepo).markSucceeded(7L, "12");
        verify(triggerRepo).recordRunResult(eq(5L), eq("12"), eq("SUCCEEDED"), any(), eq(true));
        ArgumentCaptor<RunResultNotificationBO> captor = ArgumentCaptor.forClass(RunResultNotificationBO.class);
        verify(notificationService).createRunResult(captor.capture());
        assertEquals(5L, captor.getValue().triggerId());
        assertEquals("每日巡检 已完成", captor.getValue().title());
    }

    @Test
    void shouldRejectCompletionWithoutTriggerContext() {
        AssistantCompletionTxBO tx = AssistantCompletionTxBO.of(
                event, "回答", TokenUsage.empty(), AssistantCompletionMetaBO.completed(40L));

        assertThrows(BusinessException.class, () -> service.completeAssistantAndFire(tx, event));
        verify(conversationService, never()).completeAssistant(anyLong(), anyString(), any(), any());
    }

    @Test
    void shouldRecordFailureAndLeaveAutoPauseDecisionToDatabaseCounter() {
        service.recordFailure(5L, 7L, 9L, "模型超时");

        verify(fireRepo).finish(7L, "FAILED", null, "模型超时");
        verify(triggerRepo).recordRunResult(eq(5L), isNull(), eq("FAILED"), any(), eq(false));
        // 自动暂停由数据库侧失败计数 CAS 决定，服务层总是发起判定；未达阈值时不会通知。
        verify(triggerRepo).autoPauseOnFailureThreshold(eq(5L), eq(3), anyString());
        verify(notificationService, never()).createRunResult(any());
    }

    @Test
    void shouldAutoPauseAndNotifyOnConsecutiveFailureThreshold() {
        when(triggerRepo.autoPauseOnFailureThreshold(eq(5L), eq(3), anyString())).thenReturn(true);

        service.recordFailure(5L, 7L, 9L, "模型超时");

        verify(triggerRepo).autoPauseOnFailureThreshold(eq(5L), eq(3), anyString());
        ArgumentCaptor<RunResultNotificationBO> captor = ArgumentCaptor.forClass(RunResultNotificationBO.class);
        verify(notificationService).createRunResult(captor.capture());
        assertTrue(captor.getValue().title().contains("已自动暂停"));
    }

    @Test
    void shouldConvergeCancelledFireWithoutFailureCount() {
        service.recordCancelled(5L, 7L);

        verify(fireRepo).finish(7L, "CANCELLED", null, "用户取消");
        verify(triggerRepo).recordRunResult(eq(5L), isNull(), eq("CANCELLED"), any(), eq(false));
        verify(triggerRepo, never()).autoPauseOnFailureThreshold(anyLong(), anyInt(), anyString());
    }

    @Test
    void shouldSkipExpireWhenAlreadyFinished() {
        when(fireRepo.finish(eq(7L), anyString(), any(), any())).thenReturn(false);

        service.expireFire(7L, 5L, 9L);

        verify(triggerRepo, never()).recordRunResult(anyLong(), any(), any(), any(), anyBoolean());
    }

    private TriggerEntity trigger() {
        TriggerEntity trigger = new TriggerEntity();
        trigger.setId(5L);
        trigger.setUserId(9L);
        trigger.setConversationId(10L);
        trigger.setName("每日巡检");
        return trigger;
    }
}
