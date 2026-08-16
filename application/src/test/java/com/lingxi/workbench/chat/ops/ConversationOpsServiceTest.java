package com.lingxi.workbench.chat.ops;

import com.lingxi.workbench.chat.event.ConversationEventPublisher;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.ops.config.ConversationOpsProperties;
import com.lingxi.workbench.chat.ops.model.bo.MqHealthBO;
import com.lingxi.workbench.chat.ops.model.vo.ConversationOpsOverviewVO;
import com.lingxi.workbench.chat.ops.model.vo.ConversationReconcileVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.service.ChatStreamService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationOpsServiceTest {

    private final ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);
    private final ChatStreamService chatStreamService = mock(ChatStreamService.class);
    private final ConversationEventPublisher eventPublisher = mock(ConversationEventPublisher.class);
    private final ConversationMqInspector mqInspector = mock(ConversationMqInspector.class);
    private final ConversationOpsMetrics metrics = mock(ConversationOpsMetrics.class);
    private final ConversationOpsProperties properties = new ConversationOpsProperties();
    private final ConversationOpsService service = new ConversationOpsService(
            messageRepo, chatStreamService, eventPublisher, mqInspector, metrics, properties);

    @Test
    void shouldExposeDatabaseBacklogWhenMqIsUnavailable() {
        when(mqInspector.health()).thenReturn(new MqHealthBO(
                false, "RocketMQ运维查询暂不可用", OffsetDateTime.now()));
        when(messageRepo.countStaleGenerating(any())).thenReturn(2L);
        when(messageRepo.countMemoryReconcileCandidates(any(), any())).thenReturn(3L);

        ConversationOpsOverviewVO result = service.overview();

        assertThat(result.isMqAvailable()).isFalse();
        assertThat(result.getStaleGenerating()).isEqualTo(2L);
        assertThat(result.getPendingMemoryReconcile()).isEqualTo(3L);
        assertThat(result.getDlqTotal()).isZero();
        verify(metrics).recordMqHealth(false);
    }

    @Test
    void shouldInterruptStaleTasksAndRepublishLocatorOnlyMemoryEvent() {
        ConversationMessageEntity candidate = new ConversationMessageEntity();
        candidate.setId(12L);
        candidate.setConversationId(10L);
        candidate.setApplicationVersionId(30L);
        when(chatStreamService.reconcileStaleGenerations(any(), anyInt())).thenReturn(2);
        when(messageRepo.findMemoryReconcileCandidates(any(), any(), anyInt()))
                .thenReturn(List.of(candidate));
        when(messageRepo.markMemoryReconciled(any(), any())).thenReturn(true);

        ConversationReconcileVO result = service.reconcile();

        assertThat(result.interruptedCount()).isEqualTo(2);
        assertThat(result.republishedCount()).isEqualTo(1);
        ArgumentCaptor<ConversationCompletedMessage> eventCaptor =
                ArgumentCaptor.forClass(ConversationCompletedMessage.class);
        verify(eventPublisher).replay(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new ConversationCompletedMessage(
                "conversation-completed:12", 10L, 12L, 30L));
        verify(metrics).recordStaleRepaired(2);
        verify(metrics).recordMemoryRepublished(1);
    }
}
