package com.lingxi.workbench.workflow.model;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.framework.json.JSONWriter;
import com.lingxi.workbench.framework.json.annotation.JSONField;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Application 发布快照中的单项能力。
 *
 * <p>草稿只需提交编码、目标类型和稳定资源主键；发布时补齐目标版本、协议、
 * 实现类型与配置校验和。模型运行时只能选择 {@code code}，不能提交资源主键。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CapabilityDefinition {

    private String code;
    private ApplicationCapabilityType type;
    private String name;
    private String description;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString)
    private Long targetId;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteLongAsString)
    private Long targetVersionId;
    private JSONObject inputSchema;
    private JSONObject outputSchema;
    private String implementationType;
    private String configurationChecksum;
    private CapabilityRiskLevel riskLevel;
    private Integer timeoutSeconds;
    private Integer maxCalls;
    private Integer resultTokenBudget;
}
