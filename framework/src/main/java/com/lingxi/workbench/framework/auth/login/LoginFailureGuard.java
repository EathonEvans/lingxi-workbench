package com.lingxi.workbench.framework.auth.login;

import com.lingxi.workbench.framework.cache.RedisKeyBuilder;
import com.lingxi.workbench.framework.cache.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败次数限制与账号锁定守卫。
 *
 * <p>按「登录类型 + 账号 + 客户端IP」维度在 Redis 中累计连续失败次数，
 * 超过阈值后锁定一段时间；成功登录即清除计数。Redis 仅承担限流计数，
 * 不承载登录态等业务数据，故障时按放行处理。</p>
 */
@RequiredArgsConstructor
public class LoginFailureGuard {

    private static final String FAILURE_COUNTER_KEY = "login:fail";
    private static final String LOCK_KEY = "login:lock";

    private final RedisUtil redisUtil;
    private final LoginProtectionProperties properties;

    /**
     * 当前客户端是否处于锁定状态。
     *
     * @param loginType 登录类型（如 admin、user），对应 Sa-Token 的 loginType
     * @param username  标准化后的账号
     */
    public boolean isLocked(String loginType, String username) {
        return Boolean.TRUE.equals(redisUtil.hasKey(lockKey(loginType, username)));
    }

    /**
     * 记录一次登录失败；窗口内连续失败达到阈值时锁定账号并重置计数。
     */
    public void recordFailure(String loginType, String username) {
        String counterKey = counterKey(loginType, username);
        Long failures = redisUtil.incrBy(counterKey, 1);
        if (Objects.equals(failures, 1L)) {
            redisUtil.expire(counterKey, properties.getFailureWindow().toSeconds(), TimeUnit.SECONDS);
        }
        if (Objects.nonNull(failures) && failures >= properties.getMaxFailures()) {
            redisUtil.setEx(lockKey(loginType, username), "1",
                    properties.getLockDuration().toSeconds(), TimeUnit.SECONDS);
            redisUtil.delete(counterKey);
        }
    }

    /**
     * 登录成功后清除失败计数与锁定标记。
     */
    public void clear(String loginType, String username) {
        redisUtil.delete(counterKey(loginType, username));
        redisUtil.delete(lockKey(loginType, username));
    }

    private String counterKey(String loginType, String username) {
        return RedisKeyBuilder.build(FAILURE_COUNTER_KEY, loginType, username, clientIp());
    }

    private String lockKey(String loginType, String username) {
        return RedisKeyBuilder.build(LOCK_KEY, loginType, username, clientIp());
    }

    /**
     * 从当前请求线程上下文读取客户端 IP；非请求线程（如异步任务）返回 unknown。
     */
    private static String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (Objects.isNull(attributes)) {
            return "unknown";
        }
        return attributes.getRequest().getRemoteAddr();
    }
}
