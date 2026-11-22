package com.lingxi.workbench.admin.skill.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发布 Skill 当前草稿请求。 */
@Data
public class PublishSkillRequest {

    @NotNull(message = "缺少草稿revision")
    @Min(value = 0, message = "草稿revision不能小于0")
    private Long expectedRevision;
    @Size(max = 64, message = "版本名称不能超过64个字符")
    private String versionName;
}
