package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.model.message.HitlResumeMessage;
import com.lingxi.workbench.chat.common.constants.ConversationMqConstants;
import com.lingxi.workbench.chat.service.ChatStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 消费已提交的 HITL 决策，批准时恢复同一运行；拒绝由本地事务直接收敛。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = ConversationMqConstants.HITL_RESUME_TOPIC,
        consumerGroup = ConversationMqConstants.HITL_RESUME_CONSUMER_GROUP,
        consumeMode = ConsumeMode.ORDERLY,
        messageModel = MessageModel.CLUSTERING,
        maxReconsumeTimes = ConversationMqConstants.MAX_RECONSUME_TIMES)
public class HitlResumeConsumer implements RocketMQListener<HitlResumeMessage> {

    private final ChatStreamService chatStreamService;

    @Override
    public void onMessage(HitlResumeMessage message) {
        chatStreamService.resumeInteraction(message.getInteractionId());
        log.info("HITL恢复事件处理完成，eventId={}，interactionId={}", message.getEventId(), message.getInteractionId());
    }
}
