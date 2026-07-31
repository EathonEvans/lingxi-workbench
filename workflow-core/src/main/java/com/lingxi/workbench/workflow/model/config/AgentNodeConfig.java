package com.lingxi.workbench.workflow.model.config;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;

import java.util.Map;

/**
 * Agent 节点配置。
 *
 * <p>领域模型中的 ID 使用 {@link Long}，执行链路不再承担字符串解析职责。
 * JSON 边界仍将 Long 写成字符串，避免管理端 JavaScript 读取雪花 ID 时发生精度丢失；
 * Fastjson2 反序列化时会将历史字符串 ID 还原为 Long。</p>
 *
 * @param agentId         Agent 主键
 * @param agentVersionId  发布时锁定的不可变 Agent 版本
 * @param inputMapping    Agent 输入名到工作流变量名的映射
 * @param outputMapping   Agent 输出名到工作流变量名的映射
 */
@JSONType(typeName = "AGENT")
public record AgentNodeConfig(
        @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString)
        Long agentId,
        @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString)
        Long agentVersionId,
        Map<String, String> inputMapping,
        Map<String, String> outputMapping) implements WorkflowNodeConfig {

    public AgentNodeConfig {
        inputMapping = inputMapping == null ? Map.of() : Map.copyOf(inputMapping);
        outputMapping = outputMapping == null ? Map.of() : Map.copyOf(outputMapping);
    }
}
