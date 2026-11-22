package com.lingxi.workbench.chat.config;

import com.lingxi.workbench.chat.common.constants.ConversationMqConstants;
import com.lingxi.workbench.chat.memory.MemoryPostProcessor;
import com.lingxi.workbench.chat.memory.consumer.ConversationSummaryConsumer;
import com.lingxi.workbench.chat.memory.consumer.InteractionRecordConsumer;
import com.lingxi.workbench.chat.memory.consumer.UserProfileConsumer;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatMemoryConfigurationTest {

    @Test
    void shouldUseIndependentConsumerGroupsWithBoundedRetries() {
        assertListener(ConversationSummaryConsumer.class, ConversationMqConstants.SUMMARY_CONSUMER_GROUP);
        assertListener(UserProfileConsumer.class, ConversationMqConstants.PROFILE_CONSUMER_GROUP);
        assertListener(InteractionRecordConsumer.class, ConversationMqConstants.INTERACTION_CONSUMER_GROUP);
    }

    @Test
    void shouldDelegateRetryToRocketMqInsteadOfLocalAsyncExecutor() throws NoSuchMethodException {
        Method method = MemoryPostProcessor.class.getMethod(
                "processSummary", ConversationCompletedMessage.class);

        assertNull(method.getAnnotation(Async.class));
    }

    private static void assertListener(Class<?> consumerType, String expectedGroup) {
        RocketMQMessageListener listener = consumerType.getAnnotation(RocketMQMessageListener.class);
        assertEquals(ConversationMqConstants.EVENT_TX_TOPIC, listener.topic());
        assertEquals(expectedGroup, listener.consumerGroup());
        assertEquals(ConversationMqConstants.MAX_RECONSUME_TIMES, listener.maxReconsumeTimes());
    }
}
