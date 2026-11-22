package com.lingxi.workbench.knowledge.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 只读离线评测集，标注期望片段；每条样本显式声明用户与索引契约。
 * @param datasetVersion 数据集版本
 * @param samples 最多 100 条评测样本
 */
public record KnowledgeEvaluationDataset(@NotBlank @Size(max = 100) String datasetVersion,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid KnowledgeEvaluationSample> samples) { }
