package com.lingxi.workbench.chat.event;

import com.lingxi.workbench.chat.common.constants.ConversationMqConstants;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionTxBO;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionMetaBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.mq.MqProducer;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.springframework.stereotype.Component;

/**
 * 通过 RocketMQ 事务消息原子完成 Assistant 终态并发布会话事件。
 */
@Component
@RequiredArgsConstructor
public class ConversationEventPublisher {

    private static final String COMPLETED_EVENT_PREFIX = "conversation-completed:";

    private final MqProducer<Object> mqProducer;

    /**
     * 先投递半消息，再由本地事务监听器完成 Assistant 终态写入。
     * 只有 Broker 已确认本地事务提交后才向调用方返回成功。
     */
    public void completeAssistant(ChatTurnBO turn, Long appVersionId, String content, TokenUsage tokenUsage, Long modelInstanceId) {
        publishCompletion(turn, appVersionId, content, tokenUsage, AssistantCompletionMetaBO.completed(modelInstanceId));
    }

    /** 将达到运行时限但已经可用的回答按降级完成提交，并继续发布记忆事件。 */
    public void completePartial(ChatTurnBO turn, Long appVersionId, String content, TokenUsage tokenUsage,
                                String code, String message) {
        publishCompletion(turn, appVersionId, content, tokenUsage, AssistantCompletionMetaBO.partial(code, message));
    }

    private void publishCompletion(ChatTurnBO turn, Long appVersionId, String content, TokenUsage tokenUsage,
                                   AssistantCompletionMetaBO metadata) {
        String eventId = COMPLETED_EVENT_PREFIX + turn.assistantMessageId();
        ConversationCompletedMessage event = new ConversationCompletedMessage(eventId, turn.conversationId(), turn.assistantMessageId(), appVersionId);
        AssistantCompletionTxBO tx = new AssistantCompletionTxBO(event, content, tokenUsage, metadata);
        TransactionSendResult result = mqProducer.sendTransaction(
                ConversationMqConstants.EVENT_TX_TOPIC, eventId, event, tx);
        if (result.getLocalTransactionState() != LocalTransactionState.COMMIT_MESSAGE) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "会话完成事务未提交");
        }
    }

    /** 已完成业务事务的运维重放只重新发布定位事件，不重复改写消息终态。 */
    public void replay(ConversationCompletedMessage event) {
        mqProducer.send(ConversationMqConstants.EVENT_TX_TOPIC, event.eventId(), event);
    }
}
