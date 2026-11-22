package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 单次请求使用的冻结路由配置；节点与映射只索引一次，不保存会话状态。 */
@Getter
public final class IntentRoutingConfig {
    private final AppRuntimeSnapshot application;
    private final RuntimeIntentTreeVersionEntity version;
    private final Map<String, IntentNodeDefinition> nodesByCode;
    private final Map<String, ApplicationIntentMapping> mappingsByCode;

    public IntentRoutingConfig(AppRuntimeSnapshot application, RuntimeIntentTreeVersionEntity version) {
        this.application = application;
        this.version = version;
        this.nodesByCode = version.getDefinitionSnapshot().nodes().stream()
                .collect(Collectors.toUnmodifiableMap(IntentNodeDefinition::getCode, Function.identity()));
        this.mappingsByCode = application.definition().intentRouting().mappings().stream()
                .collect(Collectors.toUnmodifiableMap(ApplicationIntentMapping::getIntentCode, Function.identity()));
    }

    /** 叶子允许没有执行映射，缺失时由路由入口交还 fallback Agent。 */
    public Optional<ApplicationIntentMapping> findMapping(String intentCode) {
        return Optional.ofNullable(mappingsByCode.get(intentCode));
    }
}
