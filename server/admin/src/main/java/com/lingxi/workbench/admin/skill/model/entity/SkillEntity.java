package com.lingxi.workbench.admin.skill.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import com.lingxi.workbench.framework.persistence.model.TimeEntity;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Skill 稳定主体数据库实体。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lingxi_core.ai_skill")
public class SkillEntity extends TimeEntity {

    private Long id;
    private String name;
    private String code;
    private String description;
    private SkillSourceType sourceType;
    private SkillVisibility visibility;
    private Long ownerUserId;
    private SkillLifecycleStatus status;
    private SkillSecurityStatus securityStatus;
    private Long publishedVersionId;
    private Long createdBy;
    private Long updatedBy;
}
