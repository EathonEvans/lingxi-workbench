package com.lingxi.workbench.chat.event;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.AssistantCompletionTxBO;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.chat.ops.ConversationOpsMetrics;
import com.lingxi.workbench.application.runtime.model.HitlDecisionTxBO;
import com.lingxi.workbench.application.runtime.model.message.HitlResumeMessage;
import com.lingxi.workbench.application.runtime.service.interaction.HitlDecisionTxService;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/** 会话完成事务的本地事务执行器和 PostgreSQL 状态回查器。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQTransactionListener
public class ConversationEventTxListener implements RocketMQLocalTransactionListener {

    private final ConversationService conversationService;
    private final ConversationOpsMetrics metrics;
    private final HitlDecisionTxService hitlDecisionTxService;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object arg) {
        if (arg instanceof HitlDecisionTxBO tx) {
            return executeHitl(message, tx);
        }
        if (!(arg instanceof AssistantCompletionTxBO tx)) {
            log.error("会话完成事务缺少本地事务参数");
            metrics.recordTxRollback();
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        ConversationCompletedMessage event;
        try {
            event = decode(message);
        } catch (RuntimeException exception) {
            log.error("会话完成事务消息体无效", exception);
            metrics.recordTxRollback();
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        if (!Objects.equals(event, tx.event())) {
            log.error("会话完成事务消息与本地参数不一致，eventId={}", event.eventId());
            metrics.recordTxRollback();
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            conversationService.completeAssistant(
                    event.assistantMessageId(), tx.content(), tx.tokenUsage(), tx.metadata());
            metrics.recordTxCommit();
            return RocketMQLocalTransactionState.COMMIT;
        } catch (RuntimeException exception) {
            log.warn("会话完成本地事务执行异常，eventId={}，assistantMessageId={}",
                    event.eventId(), event.assistantMessageId(), exception);
            return resolveState(event);
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        metrics.recordTxCheck();
        try {
            if (isHitlMessage(message)) {
                return resolveHitlState(decodeHitl(message));
            }
            return resolveState(decode(message));
        } catch (RuntimeException exception) {
            log.warn("会话完成事务状态回查异常", exception);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    private RocketMQLocalTransactionState executeHitl(Message message, HitlDecisionTxBO tx) {
        HitlResumeMessage event;
        try {
            event = decodeHitl(message);
        } catch (RuntimeException exception) {
            log.error("HITL事务消息体无效", exception);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        if (!Objects.equals(event.getEventId(), tx.getEvent().getEventId())
                || !Objects.equals(event.getInteractionId(), tx.getEvent().getInteractionId())) {
            log.error("HITL事务消息与本地参数不一致，interactionId={}", event.getInteractionId());
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            hitlDecisionTxService.decide(tx);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (BusinessException exception) {
            log.warn("HITL本地事务被业务规则拒绝，interactionId={}，message={}",
                    event.getInteractionId(), exception.getMessage());
            return RocketMQLocalTransactionState.ROLLBACK;
        } catch (RuntimeException exception) {
            log.warn("HITL本地事务执行异常，interactionId={}", event.getInteractionId(), exception);
            return resolveHitlState(event);
        }
    }

    private RocketMQLocalTransactionState resolveHitlState(HitlResumeMessage event) {
        try {
            return hitlDecisionTxService.transactionState(event)
                    .map(committed -> committed
                            ? RocketMQLocalTransactionState.COMMIT
                            : RocketMQLocalTransactionState.ROLLBACK)
                    .orElse(RocketMQLocalTransactionState.UNKNOWN);
        } catch (RuntimeException exception) {
            log.warn("查询HITL事务状态失败，interactionId={}", event.getInteractionId(), exception);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    /** PostgreSQL 消息终态是事务回查唯一依据，Redis 不参与决策。 */
    private RocketMQLocalTransactionState resolveState(ConversationCompletedMessage event) {
        try {
            Optional<MessageStatus> optionalStatus = conversationService.findAssistantCompletionStatus(
                    event.conversationId(), event.assistantMessageId(), event.appVersionId());
            if (optionalStatus.isEmpty()) {
                metrics.recordTxRollback();
                return RocketMQLocalTransactionState.ROLLBACK;
            }
            MessageStatus status = optionalStatus.get();
            if (status == MessageStatus.COMPLETED) {
                return RocketMQLocalTransactionState.COMMIT;
            }
            if (status == MessageStatus.GENERATING) {
                return RocketMQLocalTransactionState.UNKNOWN;
            }
            metrics.recordTxRollback();
            return RocketMQLocalTransactionState.ROLLBACK;
        } catch (RuntimeException exception) {
            log.warn("查询会话完成事务状态失败，eventId={}，assistantMessageId={}",
                    event.eventId(), event.assistantMessageId(), exception);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    /** 同时兼容生产者本地对象和 Broker 回查时的字节消息体。 */
    private static ConversationCompletedMessage decode(Message message) {
        Object payload = message.getPayload();
        if (payload instanceof ConversationCompletedMessage event) {
            return event;
        }
        if (payload instanceof byte[] bytes) {
            return requireEvent(JSON.parseObject(bytes, ConversationCompletedMessage.class));
        }
        if (payload instanceof String text) {
            return requireEvent(JSON.parseObject(text, ConversationCompletedMessage.class));
        }
        return requireEvent(JSON.parseObject(
                JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8), ConversationCompletedMessage.class));
    }

    private static HitlResumeMessage decodeHitl(Message message) {
        return JSON.parseObject(payloadText(message), HitlResumeMessage.class);
    }

    private static boolean isHitlMessage(Message message) {
        return JSON.parseObject(payloadText(message)).containsKey("interactionId");
    }

    private static String payloadText(Message message) {
        Object payload = message.getPayload();
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (payload instanceof String text) {
            return text;
        }
        return JSON.toJSONString(payload);
    }

    private static ConversationCompletedMessage requireEvent(ConversationCompletedMessage event) {
        return Objects.requireNonNull(event, "会话完成事务消息体不能为空");
    }
}
