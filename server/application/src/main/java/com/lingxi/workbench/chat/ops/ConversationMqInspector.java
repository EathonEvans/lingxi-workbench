package com.lingxi.workbench.chat.ops;

import com.lingxi.workbench.chat.common.constants.ConversationMqConstants;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.ops.common.enums.ConversationDlqGroup;
import com.lingxi.workbench.chat.ops.model.bo.MqHealthBO;
import com.lingxi.workbench.chat.ops.model.vo.ConversationDlqMessageVO;
import com.lingxi.workbench.framework.json.JSON;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 以只读 Pull 方式检查事务 Topic 和三个消费组 DLQ，不推进任何消费位点。 */
@Component
@SuppressWarnings("deprecation")
public class ConversationMqInspector {

    private static final String INSPECTOR_GROUP = "lingxi-conversation-ops-inspector";
    private static final int MAX_PULL_BATCH = 32;

    private final String nameServer;
    private final Object startLock = new Object();
    private volatile DefaultMQPullConsumer consumer;

    public ConversationMqInspector(@Value("${rocketmq.name-server:}") String nameServer) {
        this.nameServer = nameServer;
    }

    public MqHealthBO health() {
        OffsetDateTime checkedAt = OffsetDateTime.now();
        if (nameServer == null || nameServer.isBlank()) {
            return new MqHealthBO(false, "RocketMQ NameServer未配置", checkedAt);
        }
        try {
            Set<MessageQueue> queues = consumer().fetchSubscribeMessageQueues(
                    ConversationMqConstants.EVENT_TX_TOPIC);
            boolean available = !queues.isEmpty();
            return new MqHealthBO(available,
                    available ? "事务Topic可用" : "事务Topic不存在或没有可读队列", checkedAt);
        } catch (Exception exception) {
            return new MqHealthBO(false, "RocketMQ运维查询暂不可用", checkedAt);
        }
    }

    public Map<ConversationDlqGroup, Long> dlqCounts() {
        Map<ConversationDlqGroup, Long> counts = new EnumMap<>(ConversationDlqGroup.class);
        for (ConversationDlqGroup group : ConversationDlqGroup.values()) {
            counts.put(group, dlqCount(group));
        }
        return counts;
    }

    public List<ConversationDlqMessageVO> latest(int limit) {
        List<ConversationDlqMessageVO> messages = new ArrayList<>();
        int perGroupLimit = Math.max(1, Math.min(limit, MAX_PULL_BATCH));
        for (ConversationDlqGroup group : ConversationDlqGroup.values()) {
            messages.addAll(latest(group, perGroupLimit));
        }
        return messages.stream()
                .sorted(Comparator.comparing(ConversationDlqMessageVO::getStoredAt).reversed())
                .limit(limit)
                .toList();
    }

    public ConversationCompletedMessage requireEvent(ConversationDlqGroup group, String msgId) {
        try {
            MessageExt message = consumer().viewMessage(dlqTopic(group), msgId);
            return decode(message);
        } catch (Exception exception) {
            throw new IllegalStateException("死信消息不存在或RocketMQ查询不可用", exception);
        }
    }

    private long dlqCount(ConversationDlqGroup group) {
        try {
            long total = 0L;
            for (MessageQueue queue : consumer().fetchSubscribeMessageQueues(dlqTopic(group))) {
                total += Math.max(consumer().maxOffset(queue) - consumer().minOffset(queue), 0L);
            }
            return total;
        } catch (Exception exception) {
            // RocketMQ只在首次产生死信后创建DLQ Topic；Topic尚不存在等价于当前死信数为零。
            if (isTopicAbsent(exception)) {
                return 0L;
            }
            throw new IllegalStateException("RocketMQ死信数量查询失败，group=" + group.getCode(), exception);
        }
    }

    private List<ConversationDlqMessageVO> latest(ConversationDlqGroup group, int limit) {
        try {
            List<ConversationDlqMessageVO> messages = new ArrayList<>();
            for (MessageQueue queue : consumer().fetchSubscribeMessageQueues(dlqTopic(group))) {
                long minOffset = consumer().minOffset(queue);
                long maxOffset = consumer().maxOffset(queue);
                long startOffset = Math.max(minOffset, maxOffset - limit);
                PullResult result = consumer().pull(queue, "*", startOffset, limit);
                if (result.getPullStatus() != PullStatus.FOUND) {
                    continue;
                }
                result.getMsgFoundList().stream()
                        .map(message -> toVO(group, message))
                        .forEach(messages::add);
            }
            return messages;
        } catch (Exception exception) {
            // 从未产生过死信时没有对应Topic，明细应返回空集合而不是误报运维故障。
            if (isTopicAbsent(exception)) {
                return List.of();
            }
            throw new IllegalStateException("RocketMQ死信明细查询失败，group=" + group.getCode(), exception);
        }
    }

    static boolean isTopicAbsent(Throwable throwable) {
        Throwable current = throwable;
        while (Objects.nonNull(current)) {
            if (current instanceof MQClientException clientException
                    && clientException.getResponseCode() == ResponseCode.TOPIC_NOT_EXIST) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ConversationDlqMessageVO toVO(ConversationDlqGroup group, MessageExt message) {
        try {
            ConversationCompletedMessage event = decode(message);
            return ConversationDlqMessageVO.builder()
                    .group(group.getCode())
                    .groupName(group.getDisplayName())
                    .msgId(message.getMsgId())
                    .eventId(event.eventId())
                    .conversationId(String.valueOf(event.conversationId()))
                    .assistantMessageId(String.valueOf(event.assistantMessageId()))
                    .appVersionId(String.valueOf(event.appVersionId()))
                    .reconsumeTimes(message.getReconsumeTimes())
                    .storedAt(OffsetDateTime.ofInstant(
                            Instant.ofEpochMilli(message.getStoreTimestamp()), ZoneId.systemDefault()))
                    .build();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "RocketMQ死信消息体无效，group=" + group.getCode() + "，msgId=" + message.getMsgId(), exception);
        }
    }

    private static ConversationCompletedMessage decode(MessageExt message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        return Objects.requireNonNull(
                JSON.parseObject(body, ConversationCompletedMessage.class), "死信消息体不能为空");
    }

    private DefaultMQPullConsumer consumer() throws Exception {
        DefaultMQPullConsumer current = consumer;
        if (Objects.nonNull(current)) {
            return current;
        }
        synchronized (startLock) {
            if (Objects.nonNull(consumer)) {
                return consumer;
            }
            DefaultMQPullConsumer created = new DefaultMQPullConsumer(INSPECTOR_GROUP);
            created.setNamesrvAddr(nameServer);
            created.setInstanceName(INSPECTOR_GROUP + "-" + ProcessHandle.current().pid());
            try {
                created.start();
                consumer = created;
                return created;
            } catch (Exception exception) {
                created.shutdown();
                throw exception;
            }
        }
    }

    private static String dlqTopic(ConversationDlqGroup group) {
        return MixAll.DLQ_GROUP_TOPIC_PREFIX + group.getConsumerGroup();
    }

    @PreDestroy
    void shutdown() {
        DefaultMQPullConsumer current = consumer;
        if (Objects.nonNull(current)) {
            current.shutdown();
        }
    }
}
