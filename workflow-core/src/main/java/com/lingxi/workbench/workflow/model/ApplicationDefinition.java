package com.lingxi.workbench.workflow.model;

/**
 * Application 发布版本中的完整行为定义。
 *
 * <p>V1 只包含主 Workflow，后续权限、渠道和运行策略继续在该快照中演进。</p>
 */
public record ApplicationDefinition(
        int schemaVersion,
        WorkflowDefinition workflow) {
}
