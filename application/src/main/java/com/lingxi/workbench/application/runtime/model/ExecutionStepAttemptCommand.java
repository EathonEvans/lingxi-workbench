package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 追加一次已结束步骤尝试所需的数据。 */
@Getter
@Builder
public class ExecutionStepAttemptCommand {

    private String runId;
    private Integer planNo;
    private Integer attemptNo;
    private PlanStep step;
    private ExecutionStepStatus status;
    private String resultContent;
    private String errorCode;
    private OffsetDateTime startedAt;
}
