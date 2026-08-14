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
 * <p>主 Agent 与不可变能力目录构成执行边界，可选快速路由只负责确定性直达。</p>
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

    /**
     * 可选快速路由；未命中时进入 fallback Agent。
     */
    private ApplicationIntentBinding optionalFastRoutes;

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

    public ApplicationIntentBinding optionalFastRoutes() {
        return optionalFastRoutes;
    }

    public MemoryConfig memoryConf() {
        return memoryConf == null ? MemoryConfig.disabled() : memoryConf;
    }

    public ApplicationContextPolicy contextPolicy() {
        return contextPolicy == null ? ApplicationContextPolicy.defaults() : contextPolicy;
    }

    public FallbackAgent fallback() {
        return fallback;
    }

    public CapabilityCatalogSnapshot capabilityCatalog() {
        return capabilityCatalog == null ? CapabilityCatalogSnapshot.empty() : capabilityCatalog;
    }

    public ApplicationRuntimePolicy runtimePolicy() {
        return runtimePolicy == null ? ApplicationRuntimePolicy.defaults() : runtimePolicy;
    }
}
