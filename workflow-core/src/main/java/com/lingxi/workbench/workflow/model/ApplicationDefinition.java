package com.lingxi.workbench.workflow.model;

import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Application 发布版本中的完整行为定义。
 *
 * <p>Schema V5 以默认 ReAct Agent 和不可变能力目录作为执行边界，可选快速路由只负责
 * 确定性直达。{@code workflow} 和 {@code intentBinding} 只用于兼容迁移前的发布快照。</p>
 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationDefinition {

    private int schemaVersion;

    /** 迁移前 Application 内嵌的主 Workflow，仅用于历史版本兼容。 */
    private WorkflowDefinition workflow;

    private AgentCollaborationDefinition collaboration;

    /** V1-V4 强制意图路由配置，仅用于兼容已发布历史版本。 */
    private ApplicationIntentBinding intentBinding;

    /** Schema V5 可选快速路由；未命中时进入 Planner 或默认 ReAct Agent。 */
    private ApplicationIntentBinding optionalFastRoutes;

    private MemoryConfig memory;

    private ApplicationContextPolicy contextPolicy;

    /** Schema V4 默认执行入口；发布后必须锁定 ReAct AgentVersion。 */
    private ApplicationAgentReference defaultAgent;

    private PlannerPolicy plannerPolicy;

    private CapabilityCatalogSnapshot capabilityCatalog;

    private ApplicationRuntimePolicy runtimePolicy;

    public int schemaVersion() {
        return schemaVersion;
    }

    public WorkflowDefinition workflow() {
        return workflow;
    }

    public AgentCollaborationDefinition collaboration() {
        return collaboration;
    }

    public ApplicationIntentBinding intentBinding() {
        return intentBinding;
    }

    public ApplicationIntentBinding optionalFastRoutes() {
        return optionalFastRoutes;
    }

    public MemoryConfig memory() {
        return memory == null ? MemoryConfig.disabled() : memory;
    }

    public ApplicationContextPolicy contextPolicy() {
        return contextPolicy == null ? ApplicationContextPolicy.defaults() : contextPolicy;
    }

    public ApplicationAgentReference defaultAgent() {
        return defaultAgent;
    }

    public PlannerPolicy plannerPolicy() {
        return plannerPolicy == null ? PlannerPolicy.disabled() : plannerPolicy;
    }

    public CapabilityCatalogSnapshot capabilityCatalog() {
        return capabilityCatalog == null ? CapabilityCatalogSnapshot.empty() : capabilityCatalog;
    }

    public ApplicationRuntimePolicy runtimePolicy() {
        return runtimePolicy == null ? ApplicationRuntimePolicy.defaults() : runtimePolicy;
    }

    /** 兼容迁移前只包含内嵌 Workflow 的快照构造。 */
    public ApplicationDefinition(int schemaVersion, WorkflowDefinition workflow) {
        this(schemaVersion, workflow, null, null, null, MemoryConfig.disabled(), ApplicationContextPolicy.defaults(),
                null, PlannerPolicy.disabled(), CapabilityCatalogSnapshot.empty(), ApplicationRuntimePolicy.defaults());
    }

    /** 兼容迁移前包含协作配置的快照构造。 */
    public ApplicationDefinition(int schemaVersion, WorkflowDefinition workflow, AgentCollaborationDefinition collaboration) {
        this(schemaVersion, workflow, collaboration, null, null, MemoryConfig.disabled(), ApplicationContextPolicy.defaults(),
                null, PlannerPolicy.disabled(), CapabilityCatalogSnapshot.empty(), ApplicationRuntimePolicy.defaults());
    }

    /** 兼容迁移期调用方。 */
    public ApplicationDefinition(int schemaVersion, WorkflowDefinition workflow, AgentCollaborationDefinition collaboration, ApplicationIntentBinding intentBinding) {
        this(schemaVersion, workflow, collaboration, intentBinding, null, MemoryConfig.disabled(), ApplicationContextPolicy.defaults(),
                null, PlannerPolicy.disabled(), CapabilityCatalogSnapshot.empty(), ApplicationRuntimePolicy.defaults());
    }
}
