package com.lingxi.workbench.workflow.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 工作流节点单次执行状态枚举。
 */
@Getter
@RequiredArgsConstructor
public enum WorkflowNodeStatus {
    /**
     * 节点执行成功，产生有效输出。
     */
    SUCCEEDED("SUCCEEDED", "执行成功"),
    /**
     * 节点执行失败，可通过追踪记录中的 errorMessage 获取失败原因。
     */
    FAILED("FAILED", "执行失败");

    private final String code;
    private final String describe;
}
