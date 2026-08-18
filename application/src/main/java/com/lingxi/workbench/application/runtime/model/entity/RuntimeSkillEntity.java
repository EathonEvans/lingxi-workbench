package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import lombok.Data;

/** Skill 主体运行端只读实体，用于执行前安全熔断。 */
@Data
@TableName("lingxi_core.ai_skill")
public class RuntimeSkillEntity {

    private Long id;
    private String code;
    private SkillLifecycleStatus status;
    private SkillSecurityStatus securityStatus;
}
