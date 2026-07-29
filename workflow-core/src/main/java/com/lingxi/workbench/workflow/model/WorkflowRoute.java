package com.lingxi.workbench.workflow.model;

/**
 * 条件节点的确定性分支。
 */
public enum WorkflowRoute {
    /** 条件匹配 */
    MATCHED,
    /** 条件不匹配 */
    NOT_MATCHED
}
