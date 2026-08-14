package com.lingxi.workbench.framework.auth.login;

import com.lingxi.workbench.framework.cache.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginFailureGuardTest {

    private final RedisUtil redisUtil = mock(RedisUtil.class);
    private final LoginFailureGuard guard = new LoginFailureGuard(redisUtil, new LoginProtectionProperties());

    @Test
    void shouldNotBeLockedInitially() {
        when(redisUtil.hasKey(anyString())).thenReturn(false);

        assertFalse(guard.isLocked("admin", "alice"));
    }

    @Test
    void shouldLockAfterReachingMaxFailures() {
        // 连续 5 次失败达到默认阈值
        when(redisUtil.incrBy(anyString(), eq(1L))).thenReturn(1L, 2L, 3L, 4L, 5L);
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("admin", "alice");
        }

        // 写入锁定标记并按配置时长过期
        verify(redisUtil).setEx(anyString(), eq("1"), eq(600L), eq(TimeUnit.SECONDS));
        // 锁定后拒绝登录
        when(redisUtil.hasKey(anyString())).thenReturn(true);
        assertTrue(guard.isLocked("admin", "alice"));
    }

    @Test
    void shouldNotLockBelowThreshold() {
        when(redisUtil.incrBy(anyString(), eq(1L))).thenReturn(1L, 2L, 3L, 4L);
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("admin", "alice");
        }

        verify(redisUtil, never()).setEx(anyString(), eq("1"), eq(600L), eq(TimeUnit.SECONDS));
    }

    @Test
    void shouldClearFailureCountersOnSuccessfulLogin() {
        guard.clear("admin", "alice");

        // 同时清除失败计数与锁定标记两个 Key
        verify(redisUtil, times(2)).delete(anyString());
    }

    @Test
    void shouldIsolateCountersByLoginType() {
        when(redisUtil.incrBy(anyString(), eq(1L))).thenReturn(1L, 2L, 3L, 4L, 5L);
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("user", "bob");
        }
        // user 端 5 次失败不影响 admin 端
        when(redisUtil.hasKey(anyString())).thenReturn(false);
        assertFalse(guard.isLocked("admin", "bob"));
    }

    @Test
    void shouldAllowLoginWhenRedisUnavailableDuringLockCheck() {
        when(redisUtil.hasKey(anyString())).thenThrow(redisUnavailable());

        assertFalse(guard.isLocked("admin", "alice"));
    }

    @Test
    void shouldKeepCredentialFailureWhenRedisCounterUnavailable() {
        when(redisUtil.incrBy(anyString(), eq(1L))).thenThrow(redisUnavailable());

        assertDoesNotThrow(() -> guard.recordFailure("admin", "alice"));
    }

    @Test
    void shouldAllowSuccessfulLoginWhenRedisCleanupUnavailable() {
        doThrow(redisUnavailable()).when(redisUtil).delete(anyString());

        assertDoesNotThrow(() -> guard.clear("admin", "alice"));
    }

    @Test
    void shouldNotHideProgrammingErrors() {
        when(redisUtil.hasKey(anyString())).thenThrow(new IllegalStateException("unexpected"));

        assertThrows(IllegalStateException.class, () -> guard.isLocked("admin", "alice"));
    }

    private static DataAccessResourceFailureException redisUnavailable() {
        return new DataAccessResourceFailureException("Redis unavailable");
    }
}
