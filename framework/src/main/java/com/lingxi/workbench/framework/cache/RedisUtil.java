package com.lingxi.workbench.framework.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 登录保护和生成事件缓存使用的 Redis 字符串操作。
 *
 * <p>调用方通过 {@link RedisKeyBuilder} 生成带业务前缀的 Key，并自行决定缓存失败的处理方式。
 * 本类只保留实际使用的操作，不捕获异常或改变底层返回语义。</p>
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate redisTemplate;

    /** 删除失效的缓存或登录失败计数。 */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /** 判断登录锁定标识是否仍然存在。 */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /** 刷新登录计数或生成事件窗口的有效期。 */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, expirationDuration(timeout, unit));
    }

    /** 写入具有明确有效期的字符串值。 */
    public void setEx(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, expirationDuration(timeout, unit));
    }

    /** 原子累加计数；首次计数的有效期由调用方设置。 */
    public Long incrBy(String key, long increment) {
        return redisTemplate.opsForValue().increment(key, increment);
    }

    /** 读取指定索引范围的缓存事件，保留 Redis 的顺序与边界语义。 */
    public List<String> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /** 将新事件追加到列表尾部。 */
    public Long lRightPush(String key, String value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    /** 只保留指定索引范围，限制生成事件缓存的大小。 */
    public void lTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    private static Duration expirationDuration(long timeout, TimeUnit unit) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("Redis 过期时间必须大于 0");
        }
        return Duration.of(timeout, Objects.requireNonNull(unit, "时间单位不能为空").toChronoUnit());
    }
}
