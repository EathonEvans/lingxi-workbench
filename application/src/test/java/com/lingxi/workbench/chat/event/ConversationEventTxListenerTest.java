package com.lingxi.workbench.chat.event;

import com.lingxi.workbench.application.runtime.model.HitlDecisionTxBO;
import com.lingxi.workbench.application.runtime.model.message.HitlResumeMessage;
import com.lingxi.workbench.application.runtime.service.interaction.HitlDecisionTxService;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionMetaBO;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionTxBO;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.application.runtime.service.trigger.TriggerRunTxService;
import com.lingxi.workbench.chat.ops.ConversationOpsMetrics;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationEventTxListenerTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationOpsMetrics metrics = mock(ConversationOpsMetrics.class);
    private final HitlDecisionTxService hitlDecisionTxService = mock(HitlDecisionTxService.class);
    private final TriggerRunTxService triggerRunTxService = mock(TriggerRunTxService.class);
    private final ConversationEventTxListener listener = new ConversationEventTxListener(
            conversationService, metrics, hitlDecisionTxService, triggerRunTxService);
    private final ConversationCompletedMessage event =
            new ConversationCompletedMessage("conversation-completed:12", 10L, 12L, 30L);

    @Test
    void shouldCommitAfterCompletingAssistantInLocalTransaction() {
        TokenUsage usage = TokenUsage.of(10, 5, 15);
        Message<ConversationCompletedMessage> message = MessageBuilder.withPayload(event).build();

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(
                message, AssistantCompletionTxBO.of(
                        event, "回答", usage, AssistantCompletionMetaBO.completed(40L)));

        assertEquals(RocketMQLocalTransactionState.COMMIT, state);
        verify(conversationService).completeAssistant(
                12L, "回答", usage, AssistantCompletionMetaBO.completed(40L));
    }

    @Test
    void shouldUsePostgresqlStatusAsOnlyTransactionCheckSource() {
        when(conversationService.findAssistantCompletionStatus(10L, 12L, 30L))
                .thenReturn(Optional.of(MessageStatus.COMPLETED));

        RocketMQLocalTransactionState state = listener.checkLocalTransaction(
                MessageBuilder.withPayload(event).build());

        assertEquals(RocketMQLocalTransactionState.COMMIT, state);
        verify(conversationService).findAssistantCompletionStatus(10L, 12L, 30L);
    }

    @Test
    void shouldKeepGeneratingTransactionUnknownForBoundedBrokerCheck() {
        when(conversationService.findAssistantCompletionStatus(10L, 12L, 30L))
                .thenReturn(Optional.of(MessageStatus.GENERATING));

        RocketMQLocalTransactionState state = listener.checkLocalTransaction(
                MessageBuilder.withPayload(event).build());

        assertEquals(RocketMQLocalTransactionState.UNKNOWN, state);
    }

    @Test
    void shouldRollbackHitlTransactionRejectedByBusinessRule() {
        HitlResumeMessage event = HitlResumeMessage.builder()
                .eventId("evt-1")
                .interactionId(19L)
                .build();
        HitlDecisionTxBO tx = HitlDecisionTxBO.builder().event(event).build();
        doThrow(new BusinessException(HttpStatusEnum.CONFLICT, "审批指纹不匹配"))
                .when(hitlDecisionTxService).decide(tx);

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(
                MessageBuilder.withPayload(event).build(), tx);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
    }
}
