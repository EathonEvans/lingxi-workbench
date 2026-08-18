package com.lingxi.workbench.admin.skill.model.vo;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Skill 草稿响应。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkillDraftVO {

    private String id;
    private String baseVersionId;
    private Integer schemaVersion;
    private String instructions;
    private SkillActivationMode activationMode;
    private List<String> requestedToolCodes;
    private Long revision;
    private String changeNote;
}
