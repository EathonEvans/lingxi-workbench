package com.lingxi.workbench.admin.evaluation.model.bo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Langfuse 评测数据集安全投影。 */
@Getter
@Builder
public class LangfuseDatasetBO {
    private final String id;
    private final String name;
    private final String description;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
