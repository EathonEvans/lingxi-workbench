package com.lingxi.workbench.admin.skill.model.request;

import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建 Skill 稳定主体请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CreateSkillRequest {

    @NotBlank(message = "请输入Skill名称")
    @Size(max = 64, message = "Skill名称不能超过64个字符")
    private String name;
    @NotBlank(message = "请输入Skill编码")
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "Skill编码仅支持小写字母、数字、中划线和下划线")
    private String code;
    @Size(max = 500, message = "Skill描述不能超过500个字符")
    private String description;
    @NotNull(message = "请选择Skill来源")
    private SkillSourceType sourceType;
    @NotNull(message = "请选择Skill可见范围")
    private SkillVisibility visibility;
}
