package com.lingxi.workbench.workflow.model.config;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.Map;

/**
 * Agent 节点配置。
 *
 * @param agentId         Agent 稳定标识，仅用于审计和展示
 * @param agentVersionId  发布时锁定的不可变 Agent 版本
 * @param inputMapping    Agent 输入名到工作流变量名的映射
 * @param outputMapping   Agent 输出名到工作流变量名的映射
 */
@JSONType(typeName = "AGENT")
public record AgentNodeConfig(
        String agentId,
        String agentVersionId,
        Map<String, String> inputMapping,
        Map<String, String> outputMapping) implements WorkflowNodeConfig {

    public AgentNodeConfig {
        inputMapping = inputMapping == null ? Map.of() : Map.copyOf(inputMapping);
        outputMapping = outputMapping == null ? Map.of() : Map.copyOf(outputMapping);
    }
}
