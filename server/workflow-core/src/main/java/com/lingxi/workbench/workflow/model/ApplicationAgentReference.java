package com.lingxi.workbench.workflow.model;

import com.lingxi.workbench.framework.json.JSONWriter;
import com.lingxi.workbench.framework.json.annotation.JSONField;

/**
 * Application 协作配置中冻结的 Agent 引用快照。
 *
 * <p>草稿阶段必须提交稳定的 Agent 主键和明确选择的 AgentVersion 主键。
 * Application 发布时，控制面校验版本归属，并将展示名称和职责描述一并写入
 * ApplicationVersion 快照。运行时因此：</p>
 * <ul>
 *   <li>不需要回查 Agent 主表（减少一次 DB 查询）</li>
 *   <li>不会受到 Agent 后续修改名称、描述等元信息的影响（快照隔离）</li>
 *   <li>不会因 Agent 发布新版本而被意外升级（版本锁定）</li>
 * </ul>
 *
 * <p>序列化说明：{@code agentId} 和 {@code agentVersionId} 使用
 * {@code WriteLongAsString} 策略，防止 JavaScript 端长整型精度丢失。</p>
 *
 * @param agentId        Agent 稳定身份主键（草稿和快照中均有值）
 * @param agentVersionId 草稿明确选择并在发布快照中冻结的 AgentVersion 主键
 * @param name           发布时冻结的 Agent 展示名称，供 Supervisor 和前端展示
 * @param description    发布时冻结的 Agent 职责描述，供 Supervisor 运行时判断委派时机
 */
public record ApplicationAgentReference(
        @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString) Long agentId,
        @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString) Long agentVersionId,
        String name,
        String description) {
}
