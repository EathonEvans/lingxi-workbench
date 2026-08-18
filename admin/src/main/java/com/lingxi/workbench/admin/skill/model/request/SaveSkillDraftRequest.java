package com.lingxi.workbench.admin.skill.model.request;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 保存 Skill 草稿请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SaveSkillDraftRequest {

    @NotBlank(message = "请输入Skill指令")
    @Size(max = 100000, message = "Skill指令不能超过100000个字符")
    private String instructions;
    @NotNull(message = "请选择Skill激活方式")
    private SkillActivationMode activationMode;
    @NotNull(message = "Tool请求列表不能为空")
    @Size(max = 20, message = "单个Skill最多请求20个Tool")
    private List<@NotBlank(message = "Tool编码不能为空")
            @Pattern(regexp = "^[a-z][a-z0-9_.-]{1,127}$", message = "Tool编码格式不正确") String> requestedToolCodes;
    @NotNull(message = "缺少草稿revision")
    @Min(value = 0, message = "草稿revision不能小于0")
    private Long expectedRevision;
    @Size(max = 255, message = "版本说明不能超过255个字符")
    private String changeNote;
}
