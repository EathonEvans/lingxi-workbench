package com.lingxi.workbench.infra.ai.skill.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** AgentVersion 发布时锁定的 SkillVersion 及其 Tool 权限交集。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentSkillBindingSnapshot {

    private Long skillId;
    private Long skillVersionId;
    private Integer versionNo;
    private String checksum;
    private SkillVersionSnapshot skill;
    /** Skill 请求且同时位于 Agent Tool 白名单中的编码。 */
    private List<String> effectiveToolCodes;
    /** Skill 请求但未获 AgentVersion 授权的编码，仅用于治理审计。 */
    private List<String> deniedToolCodes;
}
