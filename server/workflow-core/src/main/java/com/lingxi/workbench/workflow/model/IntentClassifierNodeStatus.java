package com.lingxi.workbench.workflow.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Workflow 意图分类节点的稳定 V2 状态。 */
@Getter
@RequiredArgsConstructor
public enum IntentClassifierNodeStatus {

    SELECTED("SELECTED", "已选择候选"),
    CLARIFICATION_REQUIRED("CLARIFICATION_REQUIRED", "需要澄清"),
    DEFERRED("DEFERRED", "交由固定分支处理");

    private final String code;
    private final String describe;
}
