package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** MQ 恢复同一运行时使用的可信调度上下文。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExecutionResumeContext {

    private final RuntimeCheckpoint checkpoint;
    private final Long interactionId;
    private final String fingerprint;
}
