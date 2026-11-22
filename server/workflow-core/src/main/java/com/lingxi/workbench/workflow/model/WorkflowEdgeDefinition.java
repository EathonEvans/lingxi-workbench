package com.lingxi.workbench.workflow.model;

/**
 * 工作流连线定义。
 *
 * @param sourceNodeId 源节点 ID
 * @param targetNodeId 目标节点 ID
 * @param route        分支路由，仅条件节点的出边需要设置
 */
public record WorkflowEdgeDefinition(
        String sourceNodeId,
        String targetNodeId,
        WorkflowRoute route) {

    /**
     * 创建直连边，不设置分支路由。
     *
     * @param sourceNodeId 源节点 ID
     * @param targetNodeId 目标节点 ID
     * @return 无路由的连线定义
     */
    public static WorkflowEdgeDefinition direct(String sourceNodeId, String targetNodeId) {
        return new WorkflowEdgeDefinition(sourceNodeId, targetNodeId, null);
    }

    /**
     * 创建带分支路由的连线，用于条件节点的出边。
     *
     * @param sourceNodeId 源节点 ID
     * @param targetNodeId 目标节点 ID
     * @param route        分支路由
     * @return 带路由的连线定义
     */
    public static WorkflowEdgeDefinition routed(
            String sourceNodeId,
            String targetNodeId,
            WorkflowRoute route) {
        return new WorkflowEdgeDefinition(sourceNodeId, targetNodeId, route);
    }
}
