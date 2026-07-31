package com.lingxi.workbench.chat.repo;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.chat.config.ChatGenerationEventProperties;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventCacheBO;
import com.lingxi.workbench.framework.cache.RedisKeyBuilder;
import com.lingxi.workbench.framework.cache.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.lingxi.workbench.chat.common.constants.ChatCacheConstants.GENERATION_EVENT_KEY;

/**
 * 使用 Redis 保存生成任务的短期可重放事件。
 *
 * <p>PostgreSQL 仍是消息终态的事实来源；Redis 只承担断线续接所需的增量缓冲。
 * 缓存故障会记录告警并降级到数据库终态恢复，不中断模型生成主流程。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatGenerationEventRepo {

    private final RedisUtil redisUtil;
    private final ChatGenerationEventProperties properties;

    /**
     * 追加一个带序号的文本或工具状态事件，并刷新缓存过期时间。
     */
    public void append(Long assistantMessageId, ChatGenerationEventBO event) {
        if (event.sequence() < 1) {
            return;
        }
        String key = eventKey(assistantMessageId);
        try {
            redisUtil.lRightPush(key, JSON.toJSONString(ChatGenerationEventCacheBO.from(event)));
            redisUtil.lTrim(key, -properties.getMaxEvents(), -1);
            redisUtil.expire(key, properties.getTtlHours(), TimeUnit.HOURS);
        } catch (RuntimeException exception) {
            log.warn("缓存对话生成事件失败，assistantMessageId={}，sequence={}", assistantMessageId, event.sequence(), exception);
        }
    }

    /**
     * 读取指定序号之后的可重放事件。
     */
    public List<ChatGenerationEventBO> findAfter(Long assistantMessageId, long afterSequence) {
        try {
            List<String> values = redisUtil.lRange(eventKey(assistantMessageId), 0, -1);
            List<String> cachedValues = Objects.requireNonNullElseGet(values, () -> List.of());
            return cachedValues
                    .stream()
                    .map(value -> restoreEvent(assistantMessageId, value))
                    .flatMap(Optional::stream)
                    .filter(event -> event.sequence() > afterSequence)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("读取对话生成事件缓存失败，assistantMessageId={}，afterSequence={}", assistantMessageId, afterSequence, exception);
            return List.of();
        }
    }

    private static String eventKey(Long assistantMessageId) {
        return RedisKeyBuilder.build(GENERATION_EVENT_KEY, String.valueOf(assistantMessageId));
    }

    /**
     * 单条缓存损坏时跳过该事件，避免一条脏数据导致整个任务无法续接。
     */
    private static Optional<ChatGenerationEventBO> restoreEvent(Long assistantMessageId, String value) {
        try {
            ChatGenerationEventCacheBO cachedEvent = JSON.parseObject(value, ChatGenerationEventCacheBO.class);
            return Optional.of(cachedEvent.toEvent());
        } catch (RuntimeException exception) {
            log.warn("忽略格式错误的对话生成事件缓存，assistantMessageId={}", assistantMessageId, exception);
            return Optional.empty();
        }
    }
}
