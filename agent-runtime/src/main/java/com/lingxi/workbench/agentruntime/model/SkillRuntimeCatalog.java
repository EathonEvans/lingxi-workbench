package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Application 完成版本、安全与权限复核后交给 Runtime 的冻结 Skill 目录。
 *
 * @param bindings           AgentVersion 锁定且本轮仍可执行的 SkillVersion
 * @param explicitSkillCodes 服务端已校验的用户显式选择
 */
public record SkillRuntimeCatalog(List<AgentSkillBindingSnapshot> bindings,
                                  List<String> explicitSkillCodes) {

    public SkillRuntimeCatalog {
        bindings = List.copyOf(Objects.requireNonNullElseGet(bindings, List::of));
        explicitSkillCodes = List.copyOf(Objects.requireNonNullElseGet(explicitSkillCodes, List::of));
    }

    public static SkillRuntimeCatalog empty() {
        return new SkillRuntimeCatalog(List.of(), List.of());
    }
}
