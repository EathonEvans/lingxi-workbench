package com.lingxi.workbench.admin.intent.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发布意图树草稿请求。 */
@Data
public class PublishIntentTreeRequest {

    @NotNull(message = "缺少草稿revision")
    @Min(value = 0, message = "草稿revision不能小于0")
    private Long expectedRevision;

    @Size(max = 64, message = "版本名称不能超过64个字符")
    private String versionName;
}
