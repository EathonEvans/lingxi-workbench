package com.lingxi.workbench.agentruntime.event;

import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionEventsTest {

    @Test
    void shouldPersistConcurrentEventsInPublicationOrder() throws Exception {
        RuntimeEventSink sink = mock(RuntimeEventSink.class);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicLong persistedSequence = new AtomicLong();
        List<Long> persisted = new ArrayList<>();
        when(sink.onEvent(any())).thenAnswer(call -> {
            ExecutionEvent event = call.getArgument(0);
            if (event.getType() == ExecutionEventType.MODEL_STARTED) {
                firstEntered.countDown();
                assertTrue(releaseFirst.await(3, TimeUnit.SECONDS));
            }
            synchronized (persisted) {
                persisted.add(event.getSequenceNo());
                return persistedSequence.incrementAndGet();
            }
        });
        ExecutionEvents events = new ExecutionEvents("run-1", 1L, 2L, ExecutionScope.RUN, sink, 0L);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> events.modelStarted(1, 3L, "test-model", false));
            assertTrue(firstEntered.await(3, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                events.planning(1);
            });
            try {
                assertTrue(secondStarted.await(3, TimeUnit.SECONDS));
                // 模拟父 Agent 的事件持久化尚未返回时，异步任务开始发布下一条事件。
                assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            } finally {
                releaseFirst.countDown();
            }
            first.get(3, TimeUnit.SECONDS);
            second.get(3, TimeUnit.SECONDS);
        }
        assertEquals(List.of(1L, 2L), persisted);
        assertEquals(2L, events.lastSequenceNo());
    }

    @Test
    void shouldNotAdvanceSequenceWhenPersistenceFails() {
        RuntimeEventSink sink = mock(RuntimeEventSink.class);
        when(sink.onEvent(any())).thenThrow(new IllegalStateException("storage unavailable")).thenReturn(1L);
        ExecutionEvents events = new ExecutionEvents("run-1", 1L, 2L, ExecutionScope.RUN, sink, 0L);

        assertThrows(IllegalStateException.class, () -> events.planning(1));
        assertEquals(0L, events.lastSequenceNo());
        events.planning(1);
        assertEquals(1L, events.lastSequenceNo());
    }

    @Test
    void shouldRetainGlobalSequenceReturnedByPersistenceAndDelegation() {
        RuntimeEventSink sink = mock(RuntimeEventSink.class);
        when(sink.onEvent(any())).thenReturn(8L, 12L);
        ExecutionEvents events = new ExecutionEvents("run-1", 1L, 2L, ExecutionScope.RUN, sink, 4L);
        events.planning(1);
        assertEquals(8L, events.lastSequenceNo());
        events.mergeSequence(11L);
        events.planning(2);
        assertEquals(12L, events.lastSequenceNo());
    }

    @Test
    void shouldRejectAnActualPersistedSequenceRegression() {
        RuntimeEventSink sink = mock(RuntimeEventSink.class);
        when(sink.onEvent(any())).thenReturn(4L);
        ExecutionEvents events = new ExecutionEvents("run-1", 1L, 2L, ExecutionScope.RUN, sink, 5L);
        assertThrows(IllegalStateException.class, () -> events.planning(1));
    }
}
