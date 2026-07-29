package com.lingxi.workbench.workflow.model;

import com.lingxi.workbench.workflow.model.config.WorkflowNodeConfig;

/**
 * 工作流节点定义。
 *
 * @param id     节点唯一标识
 * @param name   节点展示名称
 * @param type   节点类型
 * @param config 节点类型对应的配置
 */
public record WorkflowNodeDefinition(
        String id,
        String name,
        WorkflowNodeType type,
        WorkflowNodeConfig config) {
}
