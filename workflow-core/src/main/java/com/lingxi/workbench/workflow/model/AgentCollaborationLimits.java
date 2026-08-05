package com.lingxi.workbench.workflow.model;

/**
 * 单次用户请求中 Supervisor 委派成员 Agent 的资源边界。
 *
 * <p>这两个参数在 Application 草稿中由管理员配置，发布时写入不可变快照。
 * 运行时由 {@code AgentCollaborationScope} 在每次委派时校验：</p>
 * <ol>
 *   <li>委派前检查：当前深度是否已达到 maxDepth</li>
 *   <li>委派前检查：本次请求已委派次数是否已达到 maxDelegations</li>
 * </ol>
 *
 * <p>边界之外由 Supervisor 自行决策——它可以分多轮分别委派多个成员，
 * 也可以将复杂任务的子问题合并后一次性委派，但不能超过这两个硬限制。</p>
 *
 * @param maxDelegations 单次用户请求允许的最大 Agent 委派次数，范围 1~10
 * @param maxDepth       Agent 调用链最大委派深度。
 *                       V1 固定为 1（只允许 Supervisor → 成员），
 *                       保留该字段为后续多层嵌套预留扩展空间
 */
public record AgentCollaborationLimits(int maxDelegations, int maxDepth) {
}