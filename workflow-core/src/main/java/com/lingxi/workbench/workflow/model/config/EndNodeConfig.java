package com.lingxi.workbench.workflow.model.config;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.Map;

/**
 * 结束节点配置。
 *
 * @param outputMapping API 输出名到工作流变量名的映射
 */
@JSONType(typeName = "END")
public record EndNodeConfig(Map<String, String> outputMapping) implements WorkflowNodeConfig {

    public EndNodeConfig {
        outputMapping = outputMapping == null ? Map.of() : Map.copyOf(outputMapping);
    }
}
