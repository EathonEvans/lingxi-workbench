package com.lingxi.workbench.application.runtime.service.trigger;

import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 到期触发消息消费者；消费侧仅做轻量校验与任务启动，生成任务在后台线程池独立运行。
 */
@Component
@RocketMQMessageListener(topic = TriggerMqConstants.TRIGGER_FIRE_TOPIC,
        consumerGroup = TriggerMqConstants.TRIGGER_FIRE_CONSUMER_GROUP,
        consumeMode = ConsumeMode.ORDERLY, messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = TriggerMqConstants.MAX_RECONSUME_TIMES)
public class TriggerFireConsumer implements RocketMQListener<TriggerFireMessage> {

    private final TriggerRunService triggerRunService;

    public TriggerFireConsumer(TriggerRunService triggerRunService) {
        this.triggerRunService = triggerRunService;
    }

    @Override
    public void onMessage(TriggerFireMessage message) {
        triggerRunService.executeFire(message.getFireId(), message.getTriggerId());
    }
}
