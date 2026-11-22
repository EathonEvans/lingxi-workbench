package com.lingxi.workbench.admin.evaluation.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** 管理端评测数据集。 */
@Getter
@Builder
public class EvaluationDatasetVO {
    private final String id;
    private final String name;
    private final String description;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
