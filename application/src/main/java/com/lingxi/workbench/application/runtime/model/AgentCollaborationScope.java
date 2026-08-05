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
 * 单次用户请求（一次对话轮次）中的多 Agent 委派作用域。
 *
 * <p>发布快照（{@link AgentCollaborationDefinition}）定义的是"允许调用谁"，
 * 本对象记录的是"本次已经调用了几次、当前调用链走到哪里"。</p>
 *
 * <h2>设计要点</h2>
 * <ul>
 *   <li><b>共享计数器</b>：所有子作用域共享同一个 {@code AtomicInteger} 委派计数器，
 *       确保并发安全且任意深度的委派都能正确感知已用次数。</li>
 *   <li><b>不可变调用路径</b>：每个子作用域持有独立的 callPath，记录从 Supervisor 到当前 Agent
 *       的完整调用链。用于防止环形委派（Agent A → Agent B → Agent A）。</li>
 *   <li><b>V1 深度限制</b>：当前 callPath 长度即为委派深度。根作用域长度为 1（只有 Supervisor），
 *       成员作用域为 2。maxDepth=1 意味着成员不能再委派（nextDepth=2 > 1）。</li>
 * </ul>
 *
 * <h2>安全门控</h2>
 * <p>每次 {@link #delegateTo(ApplicationAgentReference)} 调用时串行检查：</p>
 * <ol>
 *   <li>调用者是否有权委派（canDelegate）？—— 只有 Supervisor 可以</li>
 *   <li>目标 Agent 是否在白名单中？—— 防止越权调用未授权的 Agent</li>
 *   <li>是否形成环？—— callPath 中是否已包含目标 Agent</li>
 *   <li>深度是否超限？—— callPath.size() > maxDepth</li>
 *   <li>次数是否超限？—— delegationCount > maxDelegations</li>
 * </ol>
 */
public final class AgentCollaborationScope {

    /** 发布快照中冻结的协作定义 */
    private final AgentCollaborationDefinition definition;

    /** 从 Supervisor 到当前 Agent 的调用路径（不可变） */
    private final List<Long> callPath;

    /** 共享的原子委派计数器 */
    private final AtomicInteger delegationCount;

    private AgentCollaborationScope(AgentCollaborationDefinition definition, List<Long> callPath, AtomicInteger delegationCount) {
        this.definition = Objects.requireNonNull(definition, "协作定义不能为空");
        this.callPath = List.copyOf(callPath);
        this.delegationCount = Objects.requireNonNull(delegationCount, "委派计数器不能为空");
    }

    /**
     * 根据已发布协作快照创建 Supervisor 根作用域。
     *
     * <p>校验协作快照完整性：enabled=true、supervisor 信息完整、
     * limits 非空、members 非空。任一字段缺失表示发布快照数据异常，
     * 抛出 CONFLICT 错误。</p>
     *
     * @param definition 发布快照中的协作定义
     * @return 根作用域，callPath = [supervisor.agentId]，计数器 = 0
     */
    public static AgentCollaborationScope root(AgentCollaborationDefinition definition) {
        if (Objects.isNull(definition) || !definition.enabled()
                || Objects.isNull(definition.supervisor())
                || Objects.isNull(definition.supervisor().agentId())
                || Objects.isNull(definition.supervisor().agentVersionId())
                || Objects.isNull(definition.limits())
                || definition.members().isEmpty()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application协作快照不完整");
        }
        return new AgentCollaborationScope(definition, List.of(definition.supervisor().agentId()), new AtomicInteger());
    }

    /**
     * 判断当前调用者是否可以发起委派。
     *
     * <p>条件：callPath 长度为 1（还在 Supervisor 层）且
     * callPath[0] 等于 definition.supervisor.agentId。</p>
     *
     * @param agentId 发起委派的 Agent ID
     * @return 是否可以委派
     */
    public boolean canDelegate(Long agentId) {
        return callPath.size() == 1
                && Objects.equals(definition.supervisor().agentId(), agentId)
                && Objects.equals(callPath.getFirst(), agentId);
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
     *
     * <p>这是唯一的委派入口，在调用前已经过 ReAct 循环中的工具调用校验。
     * 进入后将执行完整的五步门控检查链。</p>
     *
     * @param target 目标成员 Agent 的发布快照引用
     * @return 成员 Agent 的子作用域，计数器已 +1
     * @throws BusinessException 委派权、白名单、环检测、深度或次数任一检查不通过
     */
    public AgentCollaborationScope delegateTo(ApplicationAgentReference target) {
        Objects.requireNonNull(target, "目标成员Agent不能为空");
        // 门控 1：只有 Supervisor 可以委派
        if (!canDelegate(callPath.getLast())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前Agent无权继续委派成员Agent");
        }
        // 门控 2：目标必须匹配发布快照中的成员白名单（agentId + agentVersionId 双匹配）
        boolean allowedMember = definition.members().stream()
                .anyMatch(member -> Objects.equals(member.agentId(), target.agentId())
                        && Objects.equals(member.agentVersionId(), target.agentVersionId()));
        if (!allowedMember) {
            throw new BusinessException(HttpStatusEnum.FORBIDDEN, "目标Agent不在Application成员白名单中");
        }
        // 门控 3：调用链不允许形成环
        if (callPath.contains(target.agentId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent协作调用链不允许形成环");
        }
        // 门控 4：深度限制
        int nextDepth = callPath.size();
        if (nextDepth > definition.limits().maxDepth()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent协作超过最大委派深度");
        }
        // 门控 5：次数限制（原子操作）
        int used = delegationCount.incrementAndGet();
        if (used > definition.limits().maxDelegations()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent协作超过最大委派次数");
        }
        // 全部通过 → 创建子作用域（新 callPath = 父 callPath + target.agentId）
        List<Long> childPath = new ArrayList<>(callPath);
        childPath.add(target.agentId());
        return new AgentCollaborationScope(definition, childPath, delegationCount);
    }
}