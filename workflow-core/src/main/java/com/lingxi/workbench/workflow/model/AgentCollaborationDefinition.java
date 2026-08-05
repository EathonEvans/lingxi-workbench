package com.lingxi.workbench.workflow.model;

import java.util.List;

/**
 * Application 级多 Agent 协作定义，是 ApplicationVersion 发布快照的组成部部分。
 *
 * <p>核心设计原则：</p>
 * <ul>
 *   <li><b>协作关系属于 Application，不属于 Agent</b>：
 *       Agent 自身只维护独立的模型、Prompt、工具和知识库配置。
 *       谁担任 Supervisor、可以委派给谁，由 Application 在发布时决定。</li>
 *   <li><b>发布时冻结</b>：Application 发布时，Supervisor 和成员 Agent 的当前生效版本
 *       被解析并写入 {@link ApplicationAgentReference#agentVersionId()}，
 *       之后任一 Agent 发布新版本都不影响该 ApplicationVersion。</li>
 *   <li><b>V1 单层委派</b>：当前只支持 Supervisor → 成员 Agent 的一级委派，
 *       成员 Agent 不再继续委派。深度固定为 1。</li>
 * </ul>
 *
 * <p>运行时流程：Application 收到请求 → 解析 ApplicationVersion 快照 →
 * 如果 collaboration.enabled → 启动 Supervisor Agent 的 ReAct 循环 →
 * Supervisor 通过 {@code AgentDelegationTool} 将子任务委派给成员 Agent →
 * 成员结果回传 Supervisor 整合为最终回答。</p>
 *
 * <p>成员白名单约束：</p>
 * <ul>
 *   <li>最少 1 个、最多 8 个成员</li>
 *   <li>Supervisor 不能同时出现在成员列表中</li>
 *   <li>成员 Agent 当前仅支持 RAG_CHAT 类型</li>
 *   <li>成员必须是已发布状态的 Agent</li>
 * </ul>
 *
 * @param enabled    是否启用 Supervisor 协作。false 时走普通 Workflow 编排
 * @param supervisor Supervisor Agent 的快照引用，含 agentId、agentVersionId、名称和职责描述
 * @param members    允许被 Supervisor 委派的成员 Agent 白名单快照，按配置顺序排列
 * @param limits     单次用户请求的委派资源边界（最大次数 + 最大深度）
 */
public record AgentCollaborationDefinition(
        boolean enabled,
        ApplicationAgentReference supervisor,
        List<ApplicationAgentReference> members,
        AgentCollaborationLimits limits) {

    public AgentCollaborationDefinition {
        members = members == null ? List.of() : List.copyOf(members);
    }
}