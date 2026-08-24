package com.lingxi.workbench.agentruntime.plan;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 能力适配器返回给 Runtime 的业务结果；实际消耗通过调用上下文统一回传。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RuntimeCapabilityResult {

    private final String content;
    private final RuntimeInterruption interruption;

    public boolean isInterrupted() {
        return interruption != null;
    }
}
