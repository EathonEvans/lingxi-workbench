package com.lingxi.workbench.chat.model.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 用户端可展示的Plan-and-Execute时间线事件。 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatExecutionTimelineVO {

    private long sequence;
    private String runId;
    private String eventType;
    private Integer planNo;
    private String stepId;
    private String displayName;
    private String stepStatus;
    private String message;
    private List<PlanStepVO> steps;

    /** 单个安全计划步骤。 */
    public record PlanStepVO(String stepId, String name, String capabilityCode) {
    }
}
