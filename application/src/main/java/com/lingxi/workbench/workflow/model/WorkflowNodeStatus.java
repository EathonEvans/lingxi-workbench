package com.lingxi.workbench.workflow.model;

/**
 * 工作流节点单次执行状态枚举。
 */
public enum WorkflowNodeStatus {
    /**
     * 节点执行成功，产生有效输出。
     */
    SUCCEEDED,
    /**
     * 节点执行失败，可通过追踪记录中的 errorMessage 获取失败原因。
     */
    FAILED
}
