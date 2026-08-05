package com.lingxi.workbench.workflow.model.intent;

import java.util.List;

/**
 * 意图树的核心定义协议，同时作为管理端草稿编辑和发布版本不可变快照的数据载体。
 *
 * <p>该对象不绑定任何 Agent 或 Workflow，只描述"用户想做什么"。Application
 * 通过 {@link ApplicationIntentBinding} 将叶子意图映射到具体执行目标。</p>
 *
 * <p>发布为 {@code IntentTreeVersion} 时，整个定义以 JSONB 快照形式持久化，
 * 其中的 {@link IntentNodeDefinition} 树结构和 {@link IntentRoutingPolicy}
 * 均不可再修改。</p>
 *
 * @param schemaVersion             定义协议版本号，当前为 1
 * @param embeddingModelInstanceId  发布时为所有意图生成向量的 Embedding 模型实例主键，
 *                                  由管理员在草稿中选择，发布后不可变更
 * @param routingPolicy             默认候选召回策略，包含 Top-K、召回阈值和歧义分差，
 *                                  可在 Application 绑定层按场景覆盖
 * @param nodes                     全量树节点定义，包含 ROUTER 分类节点和 LEAF 叶子意图，
 *                                  通过 parentId 形成树形层级，最大深度为 3
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