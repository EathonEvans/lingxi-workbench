package com.lingxi.workbench.agentruntime.model;

import java.util.List;

/**
 * Spring AI structured output 返回的按需 Skill 选择。
 *
 * @param skillCodes 本轮需要激活的 MODEL_ROUTED Skill code，可为空
 */
public record SkillRoutingDecision(List<String> skillCodes) {
}
