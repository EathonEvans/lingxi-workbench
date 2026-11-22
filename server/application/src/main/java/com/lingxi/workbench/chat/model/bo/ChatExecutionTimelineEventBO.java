package com.lingxi.workbench.chat.model.bo;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 不含模型推理、工具参数和原始结果的执行时间线事件。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatExecutionTimelineEventBO {

    private String runId;
    private ExecutionEventType eventType;
    private Integer planNo;
    private String stepId;
    private String displayName;
    private ExecutionStepStatus stepStatus;
    private String message;
    private List<ChatPlanStepBO> steps;

    public List<ChatPlanStepBO> steps() {
        return steps == null ? List.of() : List.copyOf(steps);
    }
}
