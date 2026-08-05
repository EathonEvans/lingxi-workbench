package com.lingxi.workbench.workflow.model.intent;

import java.util.List;

/**
 * 意图树可编辑定义及发布快照。
 *
 * @param schemaVersion 定义协议版本
 * @param embeddingModelInstanceId 向量化模型实例主键
 * @param routingPolicy 默认候选召回策略
 * @param nodes 全量树节点
 */
public record IntentTreeDefinition(
        int schemaVersion,
        Long embeddingModelInstanceId,
        IntentRoutingPolicy routingPolicy,
        List<IntentNodeDefinition> nodes) {

    public IntentTreeDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
