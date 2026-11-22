package com.lingxi.workbench.chat.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.bucket.CompareAndDeleteArgs;
import org.redisson.api.bucket.CompareAndSetArgs;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private static final Duration CANCEL_SIGNAL_TTL = Duration.ofHours(24);
    private static final Duration ACTIVE_TTL = Duration.ofMinutes(2);

    private final RedissonClient redissonClient;
    private final ConcurrentMap<Long, LocalRegistration> localRegistrations = new ConcurrentHashMap<>();

    private RTopic cancellationTopic;
    private Integer listenerId;

    /**
     * 注册当前实例持有的模型任务。注册完成后会立刻检查已写入的取消标记，避免遗漏先到的跨实例取消。
     */
    public RegistrationResult register(Long assistantMessageId, Runnable cancellationHandler,
                                       Runnable ownershipLostHandler) {
        requireAssistantMessageId(assistantMessageId);
        Objects.requireNonNull(cancellationHandler, "生成取消处理器不能为空");
        Objects.requireNonNull(ownershipLostHandler, "生成所有权丢失处理器不能为空");
        subscribeIfNecessary();
        LocalRegistration registration = new LocalRegistration(
                UUID.randomUUID().toString(), cancellationHandler, ownershipLostHandler);
        if (localRegistrations.putIfAbsent(assistantMessageId, registration) != null) {
            return RegistrationResult.ACTIVE_ELSEWHERE;
        }
        try {
            RBucket<String> activeBucket = redissonClient.getBucket(activeKey(assistantMessageId));
            if (!activeBucket.setIfAbsent(registration.ownerToken, ACTIVE_TTL)) {
                localRegistrations.remove(assistantMessageId, registration);
                return RegistrationResult.ACTIVE_ELSEWHERE;
            }
            registration.owned = true;
            if (isCancellationRequested(assistantMessageId)) {
                handleSignal(assistantMessageId, registration.cancellationHandler);
            }
            return RegistrationResult.ACQUIRED;
        } catch (RuntimeException exception) {
            try {
                unregister(assistantMessageId);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    /** 当前实例任务结束后注销本机处理器和存活租约，不删除跨实例取消标记。 */
    public void unregister(Long assistantMessageId) {
        LocalRegistration registration = localRegistrations.remove(assistantMessageId);
        if (registration == null || !registration.owned) {
            return;
        }
        redissonClient.<String>getBucket(activeKey(assistantMessageId)).compareAndDelete(
                CompareAndDeleteArgs.expected(registration.ownerToken));
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
        return value != null && !value.isBlank();
    }

    /** 本机仍持有的任务定期续租，避免其他实例把长时间 Agent/Tool 执行误判为进程已退出。 */
    @Scheduled(fixedDelay = 30_000L)
    void refreshActiveTasks() {
        for (Map.Entry<Long, LocalRegistration> entry : localRegistrations.entrySet()) {
            try {
                refreshActive(entry.getKey(), entry.getValue());
            } catch (RuntimeException exception) {
                log.warn("生成任务Redis所有权续租失败，assistantMessageId={}", entry.getKey(), exception);
            }
        }
    }

    private void refreshActive(Long assistantMessageId, LocalRegistration registration) {
        if (!registration.owned || localRegistrations.get(assistantMessageId) != registration) {
            return;
        }
        RBucket<String> activeBucket = redissonClient.getBucket(activeKey(assistantMessageId));
        boolean refreshed = activeBucket.compareAndSet(CompareAndSetArgs
                .expected(registration.ownerToken)
                .set(registration.ownerToken)
                .timeToLive(ACTIVE_TTL));
        if (!refreshed && localRegistrations.get(assistantMessageId) == registration) {
            refreshed = activeBucket.setIfAbsent(registration.ownerToken, ACTIVE_TTL);
        }
        if (localRegistrations.get(assistantMessageId) != registration) {
            if (refreshed) {
                activeBucket.compareAndDelete(CompareAndDeleteArgs.expected(registration.ownerToken));
            }
            return;
        }
        if (!refreshed && localRegistrations.remove(assistantMessageId, registration)) {
            registration.owned = false;
            log.error("生成任务Redis所有权已被其他实例接管，停止本机重复执行，assistantMessageId={}", assistantMessageId);
            handleSignal(assistantMessageId, registration.ownershipLostHandler);
        }
    }

    private synchronized void subscribeIfNecessary() {
        if (cancellationTopic != null) {
            return;
        }
        cancellationTopic = redissonClient.getTopic(CANCEL_TOPIC);
        listenerId = cancellationTopic.addListener(String.class, (channel, payload) -> {
            try {
                Long assistantMessageId = Long.valueOf(payload);
                LocalRegistration registration = localRegistrations.get(assistantMessageId);
                if (registration != null) {
                    handleSignal(assistantMessageId, registration.cancellationHandler);
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

    private static void handleSignal(Long assistantMessageId, Runnable handler) {
        try {
            handler.run();
        } catch (RuntimeException exception) {
            log.error("处理生成任务跨实例信号失败，assistantMessageId={}", assistantMessageId, exception);
        }
    }

    @PreDestroy
    void close() {
        if (cancellationTopic != null && listenerId != null) {
            cancellationTopic.removeListener(listenerId);
        }
        for (Map.Entry<Long, LocalRegistration> entry : localRegistrations.entrySet()) {
            try {
                Long assistantMessageId = entry.getKey();
                LocalRegistration registration = entry.getValue();
                if (registration.owned) {
                    redissonClient.<String>getBucket(activeKey(assistantMessageId)).compareAndDelete(
                            CompareAndDeleteArgs.expected(registration.ownerToken));
                }
            } catch (RuntimeException exception) {
                log.warn("清理生成任务存活租约失败，assistantMessageId={}", entry.getKey(), exception);
            }
        }
        localRegistrations.clear();
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

    public enum RegistrationResult {
        ACQUIRED,
        ACTIVE_ELSEWHERE
    }

    @RequiredArgsConstructor
    private static final class LocalRegistration {

        private final String ownerToken;
        private final Runnable cancellationHandler;
        private final Runnable ownershipLostHandler;
        private volatile boolean owned;
    }
}
