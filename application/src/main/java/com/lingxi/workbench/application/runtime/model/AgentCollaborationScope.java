package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次对话中的多 Agent 委派作用域。
 *
 * <p>发布快照定义“允许调用谁”，本对象记录“本次已经调用了几次、当前调用链在哪里”。
 * 子作用域共享原子计数器但持有不可变调用路径，因此后续即使支持并行委派，也不会
 * 因为共享可变栈而串扰。</p>
 */
public final class AgentCollaborationScope {

    private final AgentCollaborationDefinition definition;
    private final List<Long> callPath;
    private final AtomicInteger delegationCount;

    private AgentCollaborationScope(AgentCollaborationDefinition definition, List<Long> callPath, AtomicInteger delegationCount) {
        this.definition = Objects.requireNonNull(definition, "协作定义不能为空");
        this.callPath = List.copyOf(callPath);
        this.delegationCount = Objects.requireNonNull(delegationCount, "委派计数器不能为空");
    }

    /**
     * 根据已发布协作快照创建 Supervisor 根作用域。
     */
    public static AgentCollaborationScope root(AgentCollaborationDefinition definition) {
        if (Objects.isNull(definition) || !definition.enabled() || Objects.isNull(definition.supervisor()) || Objects.isNull(definition.supervisor().agentId()) || Objects.isNull(definition.supervisor().agentVersionId()) || Objects.isNull(definition.limits()) || definition.members().isEmpty()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application协作快照不完整");
        }
        return new AgentCollaborationScope(definition, List.of(definition.supervisor().agentId()), new AtomicInteger());
    }

    /**
     * 当前调用是否为允许动态委派的 Supervisor 根调用。
     */
    public boolean canDelegate(Long agentId) {
        return callPath.size() == 1 && Objects.equals(definition.supervisor().agentId(), agentId) && Objects.equals(callPath.getFirst(), agentId);
    }

    /**
     * 返回 Supervisor 可见的成员 Agent 白名单快照。
     */
    public List<ApplicationAgentReference> members() {
        return definition.members();
    }

    /**
     * 返回 Application 为本次请求配置的最大成员委派次数。
     */
    public int maxDelegations() {
        return definition.limits().maxDelegations();
    }

    /**
     * 为成员 Agent 创建子作用域，同时原子占用一次委派额度。
     */
    public AgentCollaborationScope delegateTo(ApplicationAgentReference target) {
        Objects.requireNonNull(target, "目标成员Agent不能为空");
        if (!canDelegate(callPath.getLast())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前Agent无权继续委派成员Agent");
        }
        boolean allowedMember = definition.members().stream().anyMatch(member -> Objects.equals(member.agentId(), target.agentId()) && Objects.equals(member.agentVersionId(), target.agentVersionId()));
        if (!allowedMember) {
            throw new BusinessException(HttpStatusEnum.FORBIDDEN, "目标Agent不在Application成员白名单中");
        }
        if (callPath.contains(target.agentId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent协作调用链不允许形成环");
        }
        int nextDepth = callPath.size();
        if (nextDepth > definition.limits().maxDepth()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent协作超过最大委派深度");
        }
        int used = delegationCount.incrementAndGet();
        if (used > definition.limits().maxDelegations()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent协作超过最大委派次数");
        }
        List<Long> childPath = new ArrayList<>(callPath);
        childPath.add(target.agentId());
        return new AgentCollaborationScope(definition, childPath, delegationCount);
    }
}
