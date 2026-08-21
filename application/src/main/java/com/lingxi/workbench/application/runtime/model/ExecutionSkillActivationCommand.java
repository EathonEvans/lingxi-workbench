package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 将 Runtime Skill 激活事件持久化为运行审计事实所需的数据。 */
@Getter
@Builder
public class ExecutionSkillActivationCommand {

    private String runId;
    private Long agentVersionId;
    private Long skillId;
    private Long skillVersionId;
    private String skillCode;
    private String checksum;
    private SkillActivationReason reason;
    private List<String> effectiveToolCodes;
}
