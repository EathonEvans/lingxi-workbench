package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLockExecutorTest {

    @Test
    void shouldExecuteAndReleaseAfterAcquiringShortLease() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lingxi:runtime:run:1001")).thenReturn(lock);
        when(lock.tryLock(eq(0L), eq(15_000L), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        Optional<String> result = new RedisLockExecutor(client).tryExecute("lingxi:runtime:run:1001",
                Duration.ZERO, Duration.ofSeconds(15), () -> "claimed");

        assertThat(result).contains("claimed");
        verify(lock).unlock();
    }

    @Test
    void shouldSkipActionWhenAnotherInstanceOwnsLock() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lingxi:runtime:job:hitl-expiry-scan")).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        Optional<String> result = new RedisLockExecutor(client).tryExecute("lingxi:runtime:job:hitl-expiry-scan",
                Duration.ZERO, Duration.ofSeconds(30), () -> {
                    throw new AssertionError("未获得锁不得执行临界区");
                });

        assertThat(result).isEmpty();
        verify(lock, never()).unlock();
    }

    @Test
    void shouldRejectUnprefixedOrUnboundedLockArguments() {
        RedisLockExecutor executor = new RedisLockExecutor(mock(RedissonClient.class));

        assertThatThrownBy(() -> executor.tryExecute("run:1001", Duration.ZERO,
                Duration.ofSeconds(1), () -> "unexpected"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> executor.tryExecute("lingxi:runtime:run:1001", Duration.ofSeconds(6),
                Duration.ofSeconds(1), () -> "unexpected"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> executor.tryExecute("lingxi:runtime:run:1001", Duration.ZERO,
                Duration.ofMinutes(3), () -> "unexpected"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
