package com.lingxi.workbench.agentruntime.hook;

import com.lingxi.workbench.agentruntime.SkillRouter;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.model.SkillActivation;
import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 在 Agent 模型调用前按冻结激活模式选择 Skill，并写入唯一 ExecutionState。 */
@Component
@RequiredArgsConstructor
public final class SkillHook {

    private final SkillRouter skillRouter;

    public void beforeAgent(ExecutionState state, SkillRuntimeCatalog catalog, ChatStreamCommand command,
                            boolean modelRoutingAllowed) {
        Objects.requireNonNull(state, "ExecutionState不能为空");
        SkillRuntimeCatalog frozenCatalog = Objects.requireNonNullElseGet(catalog, SkillRuntimeCatalog::empty);
        Map<String, AgentSkillBindingSnapshot> bindings = new LinkedHashMap<>();
        frozenCatalog.bindings().forEach(binding -> bindings.put(binding.getSkill().getCode(), binding));
        frozenCatalog.bindings().stream()
                .filter(binding -> binding.getSkill().getActivationMode() == SkillActivationMode.ALWAYS_ON)
                .map(binding -> new SkillActivation(binding, SkillActivationReason.ALWAYS_ON))
                .forEach(state::activateSkill);
        frozenCatalog.explicitSkillCodes().stream()
                .map(bindings::get)
                .map(binding -> new SkillActivation(binding, SkillActivationReason.EXPLICIT))
                .forEach(state::activateSkill);
        List<AgentSkillBindingSnapshot> routedCandidates = frozenCatalog.bindings().stream()
                .filter(binding -> binding.getSkill().getActivationMode() == SkillActivationMode.MODEL_ROUTED)
                .toList();
        if (modelRoutingAllowed) {
            skillRouter.route(command, state, routedCandidates).stream()
                    .map(bindings::get)
                    .map(binding -> new SkillActivation(binding, SkillActivationReason.MODEL_ROUTED))
                    .forEach(state::activateSkill);
        }
    }
}
