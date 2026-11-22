package com.lingxi.workbench.chat.event;

import com.lingxi.workbench.chat.common.constants.ConversationMqConstants;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionMetaBO;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionTxBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.mq.MqProducer;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationEventPublisherTest {

    @Test
    void shouldPublishLocatorOnlyEventWithDeterministicKey() {
        @SuppressWarnings("unchecked")
        MqProducer<Object> mqProducer = mock(MqProducer.class);
        TransactionSendResult sendResult = sendResult(LocalTransactionState.COMMIT_MESSAGE);
        when(mqProducer.sendTransaction(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(sendResult);
        ConversationEventPublisher publisher = new ConversationEventPublisher(mqProducer);
        TokenUsage usage = TokenUsage.of(10, 5, 15);

        publisher.completeAssistant(new ChatTurnBO(10L, 11L, 12L, 1), 30L, "完整回答", usage, 40L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> txCaptor = ArgumentCaptor.forClass(Object.class);
        verify(mqProducer).sendTransaction(
                org.mockito.ArgumentMatchers.eq(ConversationMqConstants.EVENT_TX_TOPIC),
                org.mockito.ArgumentMatchers.eq("conversation-completed:12"),
                eventCaptor.capture(),
                txCaptor.capture());
        assertEquals(new ConversationCompletedMessage("conversation-completed:12", 10L, 12L, 30L), eventCaptor.getValue());
        assertEquals(AssistantCompletionTxBO.of(
                (ConversationCompletedMessage) eventCaptor.getValue(), "完整回答", usage,
                AssistantCompletionMetaBO.completed(40L)), txCaptor.getValue());
    }

    @Test
    void shouldRejectUncommittedTransaction() {
        @SuppressWarnings("unchecked")
        MqProducer<Object> mqProducer = mock(MqProducer.class);
        when(mqProducer.sendTransaction(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(sendResult(LocalTransactionState.ROLLBACK_MESSAGE));
        ConversationEventPublisher publisher = new ConversationEventPublisher(mqProducer);

        assertThrows(BusinessException.class, () -> publisher.completeAssistant(
                new ChatTurnBO(10L, 11L, 12L, 1), 30L, "回答", TokenUsage.empty(), 40L));
    }

    @Test
    void shouldReplayCompletedEventWithoutRewritingAssistant() {
        @SuppressWarnings("unchecked")
        MqProducer<Object> mqProducer = mock(MqProducer.class);
        ConversationEventPublisher publisher = new ConversationEventPublisher(mqProducer);
        ConversationCompletedMessage event =
                new ConversationCompletedMessage("conversation-completed:12", 10L, 12L, 30L);

        publisher.replay(event);

        verify(mqProducer).send(ConversationMqConstants.EVENT_TX_TOPIC, event.eventId(), event);
    }

    private static TransactionSendResult sendResult(LocalTransactionState state) {
        TransactionSendResult result = new TransactionSendResult();
        result.setLocalTransactionState(state);
        return result;
    }
}
