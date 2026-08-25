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
 * <p>主 Agent 与不可变能力目录构成执行边界，意图路由负责候选决策与确定性直达。</p>
 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationDefinition {

    private int schemaVersion;

    private AgentCollaborationDefinition collaboration;

    /** 意图路由绑定；未选择可执行意图时进入 fallback Agent。 */
    private ApplicationIntentBinding intentRouting;

    private MemoryConfig memoryConf;

    private ApplicationContextPolicy contextPolicy;

    /** 发布时自动生成的主 Agent 执行入口，只引用能力目录。 */
    private FallbackAgent fallback;

    private CapabilityCatalogSnapshot capabilityCatalog;

    private ApplicationRuntimePolicy runtimePolicy;

    public int schemaVersion() {
        return schemaVersion;
    }

    public AgentCollaborationDefinition collaboration() {
        return collaboration;
    }

    public ApplicationIntentBinding intentRouting() {
        return intentRouting;
    }

    public MemoryConfig memoryConf() {
        return memoryConf;
    }

    public ApplicationContextPolicy contextPolicy() {
        return contextPolicy;
    }

    public FallbackAgent fallback() {
        return fallback;
    }

    public CapabilityCatalogSnapshot capabilityCatalog() {
        return capabilityCatalog;
    }

    public ApplicationRuntimePolicy runtimePolicy() {
        return runtimePolicy;
    }

    /**
     * 已发布快照必须由 Admin 完成所有非路由顶层配置的默认值解析和版本锁定。
     *
     * <p>意图路由允许缺省，缺省时由默认 Agent 收口；其余字段缺失代表控制面或持久化数据损坏，
     * 运行端不得再次补默认值。</p>
     */
    public boolean hasCompletePublishedDefinition() {
        return collaboration != null
                && memoryConf != null
                && contextPolicy != null
                && fallback != null
                && fallback.cap() != null
                && !fallback.cap().isBlank()
                && capabilityCatalog != null
                && runtimePolicy != null;
    }
}
