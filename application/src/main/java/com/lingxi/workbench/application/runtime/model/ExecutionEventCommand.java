package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** 追加一条安全执行时间线事件所需的数据。 */
@Getter
@Builder
public class ExecutionEventCommand {

    private String runId;
    private Long sequenceNo;
    private Instant occurredAt;
    private ExecutionEventType eventType;
    private Integer planNo;
    private String stepId;
    private String displayName;
    private ExecutionStepStatus stepStatus;
    private String message;
}
