package com.lingxi.workbench.admin.skill.model.vo;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Skill 列表响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkillVO {

    private String id;
    private String name;
    private String code;
    private String description;
    private SkillSourceType sourceType;
    private SkillVisibility visibility;
    private SkillLifecycleStatus status;
    private SkillSecurityStatus securityStatus;
    private String publishedVersionId;
    private Integer publishedVersionNo;
    private boolean publishedVersionReady;
    private Long draftRevision;
    private OffsetDateTime updatedAt;
}
