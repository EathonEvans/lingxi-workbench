package com.lingxi.workbench.workflow.model;

import lombok.Builder;
import lombok.Getter;

/** 意图识别节点运行时调用参数。 */
@Getter
public class IntentClassifierNodeInvocation {

    private final String executionId;
    private final String runId;
    private final Long workflowVersionId;
    private final String nodeId;
    private final Long intentTreeId;
    private final Long intentTreeVersionId;
    private final String query;

    @Builder
    private IntentClassifierNodeInvocation(String executionId, String runId, Long workflowVersionId, String nodeId,
                                           Long intentTreeId, Long intentTreeVersionId, String query) {
        this.executionId = executionId;
        this.runId = runId;
        this.workflowVersionId = workflowVersionId;
        this.nodeId = nodeId;
        this.intentTreeId = intentTreeId;
        this.intentTreeVersionId = intentTreeVersionId;
        this.query = query;
    }
}
