package com.lingxi.workbench.workflow.model.config;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;

import java.util.Map;

/**
 * 意图识别节点配置。
 *
 * <p>节点只负责将查询识别为结构化结果，不决定后续执行目标。条件节点根据映射后的
 * status、intentCode 和 reasonCode 等变量完成确定性分支。</p>
 *
 * @param intentTreeId        意图树主键
 * @param intentTreeVersionId 发布时锁定的不可变意图树版本
 * @param queryVariable       工作流状态中的查询变量名
 * @param outputMapping       标准识别结果名到工作流变量名的映射
 */
@JSONType(typeName = "INTENT_CLASSIFIER")
public record IntentClassifierNodeConfig(
        @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString)
        Long intentTreeId,
        @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString)
        Long intentTreeVersionId,
        String queryVariable,
        Map<String, String> outputMapping) implements WorkflowNodeConfig {

    public IntentClassifierNodeConfig {
        outputMapping = outputMapping == null ? Map.of() : Map.copyOf(outputMapping);
    }
}
