package com.lingxi.workbench.workflow.model;

import java.util.List;

/**
 * Application 级多 Agent 协作定义，是 ApplicationVersion 发布快照的组成部分。
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
 * <p>运行时流程：快速路由未命中时进入主Agent，主Agent完成规划、委派、重规划和结果综合；
 * 领域专家只处理被分配的具体业务任务，不能继续委派。</p>
 *
 * @param supervisor Supervisor Agent 的快照引用，含 agentId、agentVersionId、名称和职责描述
 * @param members 发布时根据领域范围解析的专家版本快照；草稿阶段为空
 * @param excludedDomainIds 草稿中明确排除的领域主键
 * @param excludedAgentIds 草稿中明确排除的专家 Agent 主键
 */
public record AgentCollaborationDefinition(
        ApplicationAgentReference supervisor,
        List<ApplicationAgentReference> members,
        List<Long> excludedDomainIds,
        List<Long> excludedAgentIds) {

    public AgentCollaborationDefinition {
        members = members == null ? List.of() : List.copyOf(members);
        excludedDomainIds = excludedDomainIds == null ? List.of() : List.copyOf(excludedDomainIds);
        excludedAgentIds = excludedAgentIds == null ? List.of() : List.copyOf(excludedAgentIds);
    }
}
