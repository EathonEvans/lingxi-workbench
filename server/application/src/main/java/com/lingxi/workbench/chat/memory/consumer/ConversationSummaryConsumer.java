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

/** 会话摘要消费组，失败后由 RocketMQ 有界重试并最终进入该组死信队列。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = ConversationMqConstants.EVENT_TX_TOPIC,
        consumerGroup = ConversationMqConstants.SUMMARY_CONSUMER_GROUP,
        maxReconsumeTimes = ConversationMqConstants.MAX_RECONSUME_TIMES)
public class ConversationSummaryConsumer implements RocketMQListener<ConversationCompletedMessage> {

    private final MemoryPostProcessor memoryPostProcessor;
    private final ConversationOpsMetrics metrics;

    @Override
    public void onMessage(ConversationCompletedMessage message) {
        log.info("开始处理会话摘要事件，eventId={}，assistantMessageId={}", message.eventId(), message.assistantMessageId());
        try {
            memoryPostProcessor.processSummary(message);
            metrics.recordConsumerSuccess(ConversationMqConstants.SUMMARY_CONSUMER_GROUP);
        } catch (RuntimeException exception) {
            metrics.recordConsumerFailure(ConversationMqConstants.SUMMARY_CONSUMER_GROUP);
            throw exception;
        }
    }
}
