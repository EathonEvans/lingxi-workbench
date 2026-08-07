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

    private boolean enabled;
    private Long embeddingModelInstanceId;
    private Long chatModelInstanceId;
    private Long promptVersionId;
    private int topK;
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
