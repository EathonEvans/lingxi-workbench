package com.lingxi.workbench.framework.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;

/**
 * 统一消息队列生产者抽象，封装 RocketMQ 的即时、延迟和事务消息发送能力。
 *
 * @param <T> 消息体类型
 */
public interface MqProducer<T> {

    /**
     * 同步发送即时消息。
     *
     * @param topic   消息主题
     * @param message 消息体
     * @return 发送结果
     */
    SendResult send(String topic, T message);

    /**
     * 同步发送带业务标识的即时消息，用于顺序消费时路由到同一队列。
     *
     * @param topic   消息主题
     * @param key     业务标识
     * @param message 消息体
     * @return 发送结果
     */
    SendResult send(String topic, String key, T message);

    /**
     * 发送延迟消息。
     *
     * @param topic        消息主题
     * @param message      消息体
     * @param delaySeconds 延迟秒数
     * @return 发送结果
     */
    SendResult sendDelay(String topic, T message, long delaySeconds);

    /**
     * 发送带业务标识的延迟消息。
     *
     * @param topic        消息主题
     * @param key          业务标识
     * @param message      消息体
     * @param delaySeconds 延迟秒数
     * @return 发送结果
     */
    SendResult sendDelay(String topic, String key, T message, long delaySeconds);

    /**
     * 发送事务消息，向 Broker 投递半消息后交由执行本地事务并反馈提交或回滚。
     *
     * <p>调用方需注册 {@code @RocketMQTransactionListener} 实现来处理本地事务执行和回查。</p>
     *
     * @param topic   消息主题
     * @param message 消息体
     * @param arg     传递给本地事务监听器的业务参数
     * @return 事务发送结果
     */
    TransactionSendResult sendTransaction(String topic, T message, Object arg);

    /**
     * 发送带业务标识的事务消息，便于按事件标识查询和排障。
     *
     * @param topic   消息主题
     * @param key     业务标识
     * @param message 消息体
     * @param arg     仅在生产者进程内传递给本地事务监听器的业务参数
     * @return 事务发送结果
     */
    TransactionSendResult sendTransaction(String topic, String key, T message, Object arg);
}
