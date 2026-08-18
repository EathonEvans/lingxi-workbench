package com.lingxi.workbench.infra.ai.skill.model;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 不可变发布快照。
 *
 * <p>运行时只能读取 AgentVersion 锁定的本对象，禁止按 Skill 编码回查最新草稿。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkillVersionSnapshot {

    /** 快照协议版本。 */
    private Integer schemaVersion;
    /** Skill 稳定编码。 */
    private String code;
    /** 展示名称。 */
    private String name;
    /** 候选路由描述。 */
    private String description;
    /** 受控注入的工作方法正文。 */
    private String instructions;
    /** 激活方式。 */
    private SkillActivationMode activationMode;
    /** Skill 请求的 Tool 编码，不代表已经获得授权。 */
    private List<String> requestedToolCodes;
}
