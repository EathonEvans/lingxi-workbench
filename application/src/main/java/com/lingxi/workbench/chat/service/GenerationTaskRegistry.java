package com.lingxi.workbench.chat.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 跨实例维护生成任务存活租约并传递取消信号。
 *
 * <p>PostgreSQL 中 Assistant 消息的条件状态更新才是生成和取消的业务事实。本组件只保存
 * 可丢失的实例存活租约，并把已经确认的取消广播给持有模型订阅的实例；取消标记覆盖
 * Pub/Sub 订阅前后的竞态，二者都不能作为业务状态或恢复依据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationTaskRegistry {

    private static final String CANCEL_KEY_PREFIX = "lingxi:runtime:generation:cancel:";
    private static final String ACTIVE_KEY_PREFIX = "lingxi:runtime:generation:active:";
    private static final String CANCEL_TOPIC = "lingxi:runtime:generation:cancel";
    private static final String CANCELLED_VALUE = "CANCELLED";
    private static final String ACTIVE_VALUE = "ACTIVE";
    private static final Duration CANCEL_SIGNAL_TTL = Duration.ofHours(24);
    private static final Duration ACTIVE_TTL = Duration.ofMinutes(2);

    private final RedissonClient redissonClient;
    private final ConcurrentMap<Long, Runnable> localHandlers = new ConcurrentHashMap<>();

    private RTopic cancellationTopic;
    private Integer listenerId;

    /**
     * 注册当前实例持有的模型任务。注册完成后会立刻检查已写入的取消标记，避免遗漏先到的跨实例取消。
     */
    public void register(Long assistantMessageId, Runnable cancellationHandler) {
        requireAssistantMessageId(assistantMessageId);
        Objects.requireNonNull(cancellationHandler, "生成取消处理器不能为空");
        subscribeIfNecessary();
        if (localHandlers.putIfAbsent(assistantMessageId, cancellationHandler) != null) {
            throw new IllegalStateException("当前实例已经持有该Assistant生成任务");
        }
        refreshActive(assistantMessageId);
        if (isCancellationRequested(assistantMessageId)) {
            trigger(assistantMessageId, cancellationHandler);
        }
    }

    /** 当前实例任务结束后注销本机处理器和存活租约，不删除跨实例取消标记。 */
    public void unregister(Long assistantMessageId) {
        localHandlers.remove(assistantMessageId);
        redissonClient.getBucket(activeKey(assistantMessageId)).delete();
    }

    /**
     * 在数据库已确认取消后写入有限期标记并广播给持有订阅的实例。
     */
    public void requestCancellation(Long assistantMessageId) {
        requireAssistantMessageId(assistantMessageId);
        RBucket<String> bucket = redissonClient.getBucket(cancelKey(assistantMessageId));
        bucket.set(CANCELLED_VALUE, CANCEL_SIGNAL_TTL);
        redissonClient.getTopic(CANCEL_TOPIC).publish(String.valueOf(assistantMessageId));
    }

    /** 仅用于判断其他实例是否仍持有活跃模型订阅；Redis 故障必须显式失败，不能把未知当作已失活。 */
    public boolean isActive(Long assistantMessageId) {
        requireAssistantMessageId(assistantMessageId);
        String value = redissonClient.<String>getBucket(activeKey(assistantMessageId)).get();
        return ACTIVE_VALUE.equals(value);
    }

    /** 本机仍持有的任务定期续租，避免其他实例把长时间 Agent/Tool 执行误判为进程已退出。 */
    @Scheduled(fixedDelay = 30_000L)
    void refreshActiveTasks() {
        for (Long assistantMessageId : localHandlers.keySet()) {
            refreshActive(assistantMessageId);
        }
    }

    private void refreshActive(Long assistantMessageId) {
        redissonClient.<String>getBucket(activeKey(assistantMessageId)).set(ACTIVE_VALUE, ACTIVE_TTL);
    }

    private synchronized void subscribeIfNecessary() {
        if (cancellationTopic != null) {
            return;
        }
        cancellationTopic = redissonClient.getTopic(CANCEL_TOPIC);
        listenerId = cancellationTopic.addListener(String.class, (channel, payload) -> {
            try {
                Long assistantMessageId = Long.valueOf(payload);
                Runnable handler = localHandlers.get(assistantMessageId);
                if (handler != null) {
                    trigger(assistantMessageId, handler);
                }
            } catch (NumberFormatException exception) {
                log.warn("忽略格式非法的生成取消信号");
            }
        });
    }

    private boolean isCancellationRequested(Long assistantMessageId) {
        String value = redissonClient.<String>getBucket(cancelKey(assistantMessageId)).get();
        return CANCELLED_VALUE.equals(value);
    }

    private static void trigger(Long assistantMessageId, Runnable handler) {
        try {
            handler.run();
        } catch (RuntimeException exception) {
            log.error("处理跨实例取消信号失败，assistantMessageId={}", assistantMessageId, exception);
        }
    }

    @PreDestroy
    void close() {
        if (cancellationTopic != null && listenerId != null) {
            cancellationTopic.removeListener(listenerId);
        }
        for (Long assistantMessageId : localHandlers.keySet()) {
            try {
                redissonClient.getBucket(activeKey(assistantMessageId)).delete();
            } catch (RuntimeException exception) {
                log.warn("清理生成任务存活租约失败，assistantMessageId={}", assistantMessageId, exception);
            }
        }
        localHandlers.clear();
    }

    private static void requireAssistantMessageId(Long assistantMessageId) {
        if (assistantMessageId == null || assistantMessageId <= 0) {
            throw new IllegalArgumentException("Assistant消息ID必须为正数");
        }
    }

    private static String cancelKey(Long assistantMessageId) {
        return CANCEL_KEY_PREFIX + assistantMessageId;
    }

    private static String activeKey(Long assistantMessageId) {
        return ACTIVE_KEY_PREFIX + assistantMessageId;
    }
}
