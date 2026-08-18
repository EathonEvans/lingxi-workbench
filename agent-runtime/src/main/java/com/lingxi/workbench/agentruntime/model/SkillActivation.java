package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;

/**
 * 本轮经过版本、安全和预算校验后交给 Runtime 激活的 Skill。
 *
 * @param binding 发布时锁定的 SkillVersion 绑定
 * @param reason  本轮激活原因
 */
public record SkillActivation(AgentSkillBindingSnapshot binding, SkillActivationReason reason) {
}
