package com.lingxi.workbench.admin.evaluation.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 新建平台托管的 Langfuse 评测数据集。 */
@Getter
@Setter
public class CreateEvaluationDatasetRequest {

    @NotBlank(message = "请填写数据集名称")
    @Size(max = 100, message = "数据集名称不能超过100字")
    private String name;

    @Size(max = 500, message = "数据集说明不能超过500字")
    private String description;
}
