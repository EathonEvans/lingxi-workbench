package com.lingxi.workbench.workflow.model;

import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;

/**
 * Application 发布版本中的完整行为定义。
 *
 * <p>主 Workflow 负责确定性节点编排；可选的 collaboration 定义负责受控的
 * Supervisor 动态委派。两者都属于 ApplicationVersion 的不可变行为快照。</p>
 */
public record ApplicationDefinition(
        int schemaVersion,
        WorkflowDefinition workflow,
        AgentCollaborationDefinition collaboration,
        ApplicationIntentBinding intentBinding) {

    /** 兼容既有 V1 快照和调用方。 */
    public ApplicationDefinition(int schemaVersion, WorkflowDefinition workflow) {
        this(schemaVersion, workflow, null, null);
    }

    /** 兼容既有协作快照和调用方。 */
    public ApplicationDefinition(
            int schemaVersion,
            WorkflowDefinition workflow,
            AgentCollaborationDefinition collaboration) {
        this(schemaVersion, workflow, collaboration, null);
    }
}
