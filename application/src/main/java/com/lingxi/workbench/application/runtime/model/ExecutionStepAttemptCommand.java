package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.agentruntime.plan.RuntimeTaskStep;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 追加一次已结束任务委派尝试所需的数据。 */
@Getter
@Builder
public class ExecutionStepAttemptCommand {

    private String runId;
    private Integer taskRevision;
    private Integer attemptNo;
    private RuntimeTaskStep step;
    private ExecutionStepStatus status;
    private String resultContent;
    private String errorCode;
    private OffsetDateTime startedAt;
}
