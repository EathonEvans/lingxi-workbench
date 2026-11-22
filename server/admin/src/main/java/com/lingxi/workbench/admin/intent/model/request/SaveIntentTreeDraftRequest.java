package com.lingxi.workbench.admin.intent.model.request;

import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 保存意图树草稿请求。 */
@Data
public class SaveIntentTreeDraftRequest {

    @NotNull(message = "缺少意图树定义")
    @Valid
    private IntentTreeDefinition definition;

    @NotNull(message = "缺少草稿revision")
    @Min(value = 0, message = "草稿revision不能小于0")
    private Long expectedRevision;

    @Size(max = 255, message = "版本说明不能超过255个字符")
    private String changeNote;
}
