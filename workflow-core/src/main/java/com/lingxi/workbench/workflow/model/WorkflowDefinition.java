package com.lingxi.workbench.workflow.model;

import java.util.List;

/**
 * 灵析自有的工作流定义协议。
 *
 * <p>发布后的 ApplicationVersion 应保存该定义的不可变快照，执行器只消费快照。</p>
 *
 * @param schemaVersion 协议版本号，当前为 2
 * @param code          工作流编码，同一 Application 内唯一
 * @param nodes         节点定义列表
 * @param edges         连线定义列表
 */
public record WorkflowDefinition(
        int schemaVersion,
        String code,
        List<WorkflowNodeDefinition> nodes,
        List<WorkflowEdgeDefinition> edges) {

    public WorkflowDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
