package com.lingxi.workbench.workflow.model.config;

import com.alibaba.fastjson2.annotation.JSONType;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * 工作流节点配置标记接口。
 *
 * <p>该接口属于灵析自身的配置协议，不暴露具体执行框架类型。</p>
 */
@JSONType(
        typeKey = "configType",
        seeAlso = {
                StartNodeConfig.class,
                AgentNodeConfig.class,
                ConditionNodeConfig.class,
                EndNodeConfig.class
        })
public sealed interface WorkflowNodeConfig
        permits StartNodeConfig, AgentNodeConfig, ConditionNodeConfig, EndNodeConfig {

    /**
     * 显式写入配置类型，确保 JSONB 不依赖 Fastjson2 的全局 WriteClassName。
     */
    @JSONField(name = "configType")
    default String getConfigType() {
        return switch (this) {
            case StartNodeConfig ignored -> "START";
            case AgentNodeConfig ignored -> "AGENT";
            case ConditionNodeConfig ignored -> "CONDITION";
            case EndNodeConfig ignored -> "END";
        };
    }
}
