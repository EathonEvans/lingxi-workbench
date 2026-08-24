package com.lingxi.workbench.agentruntime.plan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Runtime 提交给 Application 能力适配器的可信调用。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeCapabilityInvocation {

    private final RuntimeCapability capability;
    private final Map<String, String> inputs;
    private final AgentInvocationState invocationState;
    private final Set<String> confirmedCapabilityCodes;
    private final Consumer<RuntimeCapabilityExecutionFacts> executionRecorder;
    private final AtomicBoolean executionRecorded = new AtomicBoolean();

    public Map<String, String> getInputs() {
        return Map.copyOf(inputs == null ? Map.of() : inputs);
    }

    public Set<String> getConfirmedCapabilityCodes() {
        return Set.copyOf(confirmedCapabilityCodes == null ? Set.of() : confirmedCapabilityCodes);
    }

    /** 成功、失败、取消或待对账前都必须回传实际消耗，避免父子 Runtime 的预算和事件序号分叉。 */
    public void recordExecution(RuntimeCapabilityExecutionFacts facts) {
        Objects.requireNonNull(facts, "子能力执行事实不能为空");
        if (!executionRecorded.compareAndSet(false, true)) {
            throw new IllegalStateException("同一次子能力调用不能重复记录执行事实");
        }
        Objects.requireNonNull(executionRecorder, "子能力执行事实接收器不能为空").accept(facts);
    }
}
