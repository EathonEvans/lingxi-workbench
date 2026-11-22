package com.lingxi.workbench.admin.skill.model.request;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.framework.convention.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Skill 分页查询参数。 */
@Getter
@Setter
public class SkillPageQuery extends PageQuery {

    @Size(max = 64, message = "查询关键字不能超过64个字符")
    private String keyword;
    private SkillSourceType sourceType;
    private SkillLifecycleStatus status;
    private SkillSecurityStatus securityStatus;
}
