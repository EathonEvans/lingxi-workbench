package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 跨实例短临界区的唯一 Redis 锁入口。
 *
 * <p>调用方必须传入带命名空间的键、有限等待和有限租约。锁只减少并发竞争，业务唯一性、状态
 * 前置条件和最终状态仍必须由数据库约束及 CAS 保证。</p>
 */
@Component
@RequiredArgsConstructor
public class RedisLockExecutor {

    private static final String KEY_PREFIX = "lingxi:runtime:";
    private static final Duration MAX_WAIT = Duration.ofSeconds(5);
    private static final Duration MAX_LEASE = Duration.ofMinutes(2);

    private final RedissonClient redissonClient;

    /**
     * 在获得锁时执行动作；锁已被其他实例持有时返回空结果。
     *
     * @param key     以 {@code lingxi:runtime:} 开头的锁键
     * @param wait    最长等待时长
     * @param lease   获得锁后的最大租约
     * @param action  仅包含短临界区业务逻辑的动作
     * @return 已执行动作的非空结果，或锁未取得时的空结果
     */
    public <T> Optional<T> tryExecute(String key, Duration wait, Duration lease, Supplier<T> action) {
        validate(key, wait, lease, action);
        RLock lock = redissonClient.getLock(key);
        boolean acquired;
        try {
            acquired = lock.tryLock(wait.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatusEnum.CONFLICT, "等待分布式运行锁时被中断");
        }
        if (!acquired) {
            return Optional.empty();
        }
        try {
            return Optional.of(Objects.requireNonNull(action.get(), "分布式锁动作不能返回null"));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static <T> void validate(String key, Duration wait, Duration lease, Supplier<T> action) {
        if (key == null || !key.startsWith(KEY_PREFIX) || key.length() == KEY_PREFIX.length()
                || key.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Redis运行锁键必须使用lingxi:runtime命名空间");
        }
        if (wait == null || wait.isNegative() || wait.compareTo(MAX_WAIT) > 0) {
            throw new IllegalArgumentException("Redis运行锁等待时长必须在0到5秒之间");
        }
        if (lease == null || lease.isZero() || lease.isNegative() || lease.compareTo(MAX_LEASE) > 0) {
            throw new IllegalArgumentException("Redis运行锁租约必须在0到2分钟之间");
        }
        Objects.requireNonNull(action, "Redis运行锁动作不能为空");
    }
}
