package com.lingxi.workbench.framework.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 基于 RocketMQ Spring Remoting 客户端的统一消息生产者实现。
 *
 * <p>消息转换由 RocketMQ 组件管理，Lingxi 业务代码不直接依赖其内部 JSON 实现。</p>
 *
 * @param <T> 消息体类型
 */
@Component
@ConditionalOnProperty("rocketmq.name-server")
public class RocketMQProducer<T> implements MqProducer<T> {

    private static final Logger log = LoggerFactory.getLogger(RocketMQProducer.class);

    private final RocketMQTemplate rocketMQTemplate;

    public RocketMQProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public SendResult send(String topic, T message) {
        return send(topic, null, message);
    }

    @Override
    public SendResult send(String topic, String key, T message) {
        Message<T> msg = buildMessage(key, message);
        SendResult result = rocketMQTemplate.syncSend(topic, msg);
        log.debug("消息发送成功: topic={}, key={}, msgId={}", topic, key, result.getMsgId());
        return result;
    }

    @Override
    public SendResult sendDelay(String topic, T message, long delaySeconds) {
        return sendDelay(topic, null, message, delaySeconds);
    }

    @Override
    public SendResult sendDelay(String topic, String key, T message, long delaySeconds) {
        Message<T> msg = buildMessage(key, message);
        SendResult result = rocketMQTemplate.syncSendDelayTimeSeconds(topic, msg, delaySeconds);
        log.debug("延迟消息发送成功: topic={}, key={}, delay={}s, msgId={}",
                topic, key, delaySeconds, result.getMsgId());
        return result;
    }

    @Override
    public TransactionSendResult sendTransaction(String topic, T message, Object arg) {
        return sendTransaction(topic, null, message, arg);
    }

    @Override
    public TransactionSendResult sendTransaction(String topic, String key, T message, Object arg) {
        Message<T> msg = buildMessage(key, message);
        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(topic, msg, arg);
        log.debug("事务消息发送成功: topic={}, key={}, transactionId={}", topic, key, result.getTransactionId());
        return result;
    }

    private Message<T> buildMessage(String key, T body) {
        MessageBuilder<T> builder = MessageBuilder.withPayload(body);
        if (key != null && !key.isBlank()) {
            builder.setHeader(RocketMQHeaders.KEYS, key);
        }
        return builder.build();
    }
}
