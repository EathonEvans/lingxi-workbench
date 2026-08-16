package com.lingxi.workbench.chat.ops;

import com.lingxi.workbench.chat.ops.common.enums.ConversationDlqGroup;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 会话事务、记忆消费和对账动作的低基数运行指标。 */
@Component
public class ConversationOpsMetrics {

    private final MeterRegistry registry;
    private final Counter txCommit;
    private final Counter txCheck;
    private final Counter txRollback;
    private final Counter staleRepaired;
    private final Counter memoryRepublished;
    private final Counter manualReplay;
    private final Counter lazyCreateConflict;
    private final Counter reconcileFailure;
    private final AtomicInteger mqAvailable = new AtomicInteger();
    private final Map<ConversationDlqGroup, AtomicLong> dlqCounts = new EnumMap<>(ConversationDlqGroup.class);

    public ConversationOpsMetrics(MeterRegistry registry) {
        this.registry = registry;
        txCommit = registry.counter("conversation.tx.commit.count");
        txCheck = registry.counter("conversation.tx.check.count");
        txRollback = registry.counter("conversation.tx.rollback.count");
        staleRepaired = registry.counter("conversation.reconcile.stale.count");
        memoryRepublished = registry.counter("conversation.reconcile.memory.count");
        manualReplay = registry.counter("memory.consumer.replay.count");
        lazyCreateConflict = registry.counter("conversation.lazy_create.conflict");
        reconcileFailure = registry.counter("conversation.reconcile.failure.count");
        registry.gauge("conversation.mq.available", mqAvailable);
        for (ConversationDlqGroup group : ConversationDlqGroup.values()) {
            AtomicLong count = new AtomicLong();
            dlqCounts.put(group, count);
            registry.gauge("memory.consumer.dlq.count", Tags.of("group", group.getCode()), count);
        }
    }

    public void recordTxCommit() {
        txCommit.increment();
    }

    public void recordTxCheck() {
        txCheck.increment();
    }

    public void recordTxRollback() {
        txRollback.increment();
    }

    public void recordConsumerSuccess(String group) {
        registry.counter("memory.consumer.success.count", "group", group).increment();
    }

    /** 消费异常会触发Broker重投，因此失败尝试同时作为重试压力指标。 */
    public void recordConsumerFailure(String group) {
        registry.counter("memory.consumer.retry.count", "group", group).increment();
    }

    public void recordStaleRepaired(int count) {
        staleRepaired.increment(count);
    }

    public void recordMemoryRepublished(int count) {
        memoryRepublished.increment(count);
    }

    public void recordManualReplay() {
        manualReplay.increment();
    }

    public void recordLazyCreateConflict() {
        lazyCreateConflict.increment();
    }

    public void recordMqHealth(boolean available) {
        mqAvailable.set(available ? 1 : 0);
    }

    public void updateDlqCounts(Map<ConversationDlqGroup, Long> counts) {
        dlqCounts.forEach((group, value) -> value.set(counts.getOrDefault(group, 0L)));
    }

    public void recordReconcileFailure() {
        reconcileFailure.increment();
    }
}
