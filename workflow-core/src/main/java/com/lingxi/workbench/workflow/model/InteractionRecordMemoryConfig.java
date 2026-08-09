package com.lingxi.workbench.workflow.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 交互记录提取和向量召回配置。 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InteractionRecordMemoryConfig {

    private static final int DEFAULT_TOP_K = 3;
    private static final int DEFAULT_RETENTION_DAYS = 365;

    /** 是否启用交互记录提取和检索。 */
    private boolean enabled;
    /** Embedding 模型实例主键。 */
    private Long embeddingModelInstanceId;
    /** Chat 模型实例主键（提取用）。 */
    private Long chatModelInstanceId;
    /** 已发布 PromptVersion 主键。 */
    private Long promptVersionId;
    /** 向量检索 topK，默认 3。 */
    private int topK;
    /** 交互记录保留天数，默认 365 天。 */
    private int retentionDays;

    public int effectiveTopK() {
        return topK > 0 ? topK : DEFAULT_TOP_K;
    }

    public int effectiveRetentionDays() {
        return retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
    }

    public static InteractionRecordMemoryConfig disabled() {
        return InteractionRecordMemoryConfig.builder()
                .enabled(false)
                .topK(DEFAULT_TOP_K)
                .retentionDays(DEFAULT_RETENTION_DAYS)
                .build();
    }
}
