package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** AgentScope HITL 产生的当前中断事实。 */
public final class ExecutionInterruption {

    private final AtomicReference<RuntimeInterruption> value = new AtomicReference<>();

    public void set(RuntimeInterruption interruption) {
        if (!value.compareAndSet(null, Objects.requireNonNull(interruption, "人工中断事实不能为空"))) {
            throw new IllegalStateException("Agent调用已经存在待处理人工中断");
        }
    }

    public RuntimeInterruption get() {
        return value.get();
    }

    public boolean isPresent() {
        return value.get() != null;
    }
}
