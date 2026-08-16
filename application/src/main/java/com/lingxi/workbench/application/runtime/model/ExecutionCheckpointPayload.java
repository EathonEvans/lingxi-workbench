package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 可移植的 Tool 审批恢复载荷，不保存框架对象或线程状态。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExecutionCheckpointPayload {

    private String query;
    private String capabilityCode;
    private Long agentId;
    private Long agentVersionId;
    private String toolCode;
    private String toolConfigHash;
    private String toolInput;
    private String resourceScope;
}
