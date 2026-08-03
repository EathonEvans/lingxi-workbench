package com.lingxi.workbench.workflow.model;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Application 协作配置中冻结的 Agent 引用。
 *
 * <p>草稿只需要提交稳定的 Agent 主键；发布时控制面会解析当前生效版本，并将
 * 展示名称和职责描述一并写入 ApplicationVersion 快照。运行时因此不需要回查
 * Agent 主表，也不会受到后续元信息修改的影响。</p>
 *
 * @param agentId        Agent 稳定身份主键
 * @param agentVersionId 发布时解析并冻结的 AgentVersion 主键
 * @param name           发布时冻结的 Agent 展示名称
 * @param description    发布时冻结的 Agent 职责描述，供 Supervisor 判断委派时机
 */
public record ApplicationAgentReference(
        @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString) Long agentId,
        @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString) Long agentVersionId,
        String name,
        String description) {
}
