package com.lingxi.workbench.workflow.model;

/**
 * 第一阶段支持的工作流节点类型。
 */
public enum WorkflowNodeType {
    /**
     * 开始节点
     */
    START,
    /**
     * Agent 节点
     */
    AGENT,
    /**
     * 条件分支节点
     */
    CONDITION,
    /**
     * 结束节点
     */
    END
}
