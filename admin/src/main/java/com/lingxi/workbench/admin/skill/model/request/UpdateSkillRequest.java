package com.lingxi.workbench.admin.skill.model.request;

import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 修改 Skill 元信息请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UpdateSkillRequest {

    @NotBlank(message = "请输入Skill名称")
    @Size(max = 64, message = "Skill名称不能超过64个字符")
    private String name;
    @Size(max = 500, message = "Skill描述不能超过500个字符")
    private String description;
    @NotNull(message = "请选择Skill可见范围")
    private SkillVisibility visibility;
}
