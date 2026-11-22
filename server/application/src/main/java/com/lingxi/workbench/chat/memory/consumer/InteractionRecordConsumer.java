package com.lingxi.workbench.chat.memory.consumer;

import com.lingxi.workbench.chat.common.constants.ConversationMqConstants;
import com.lingxi.workbench.chat.memory.MemoryPostProcessor;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.ops.ConversationOpsMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 交互记录消费组，依靠 Assistant 消息唯一水位实现消费幂等。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = ConversationMqConstants.EVENT_TX_TOPIC,
        consumerGroup = ConversationMqConstants.INTERACTION_CONSUMER_GROUP,
        maxReconsumeTimes = ConversationMqConstants.MAX_RECONSUME_TIMES)
public class InteractionRecordConsumer implements RocketMQListener<ConversationCompletedMessage> {

    private final MemoryPostProcessor memoryPostProcessor;
    private final ConversationOpsMetrics metrics;

    @Override
    public void onMessage(ConversationCompletedMessage message) {
        log.info("开始处理交互记录事件，eventId={}，assistantMessageId={}", message.eventId(), message.assistantMessageId());
        try {
            memoryPostProcessor.processInteraction(message);
            metrics.recordConsumerSuccess(ConversationMqConstants.INTERACTION_CONSUMER_GROUP);
        } catch (RuntimeException exception) {
            metrics.recordConsumerFailure(ConversationMqConstants.INTERACTION_CONSUMER_GROUP);
            throw exception;
        }
    }
}
