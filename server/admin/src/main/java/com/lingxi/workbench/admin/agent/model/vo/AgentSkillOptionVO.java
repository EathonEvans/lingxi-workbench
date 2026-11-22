package com.lingxi.workbench.admin.agent.model.vo;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 管理端 Agent 可绑定的平台预设 Skill。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentSkillOptionVO {

    private String id;
    private String versionId;
    private Integer versionNo;
    private String code;
    private String name;
    private String description;
    private SkillActivationMode activationMode;
    private List<String> requestedToolCodes;
}
