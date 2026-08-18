package com.lingxi.workbench.agentruntime.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Agent Runtime 唯一的生命周期事件 envelope。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExecutionEvent {

    private final String runId;
    private final long sequenceNo;
    private final Instant occurredAt;
    private final ExecutionEventType type;
    private final ExecutionStage stage;
    private final Long agentId;
    private final Long agentVersionId;
    private final String invocationId;
    private final ExecutionEventPayload payload;
}
