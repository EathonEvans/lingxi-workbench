package com.lingxi.workbench.chat.ops;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMqInspectorTest {

    @Test
    void shouldRecognizeMissingDlqTopicFromNestedRocketMqException() {
        MQClientException topicAbsent = new MQClientException(
                ResponseCode.TOPIC_NOT_EXIST, "No topic route info");
        MQClientException fetchFailed = new MQClientException(
                "Can not find Message Queue", topicAbsent);

        assertThat(ConversationMqInspector.isTopicAbsent(fetchFailed)).isTrue();
    }

    @Test
    void shouldNotTreatOtherRocketMqErrorsAsMissingTopic() {
        MQClientException unavailable = new MQClientException(
                ResponseCode.SERVICE_NOT_AVAILABLE, "Broker unavailable");

        assertThat(ConversationMqInspector.isTopicAbsent(unavailable)).isFalse();
    }
}
