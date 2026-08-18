package com.lingxi.workbench.agentruntime.hook;

import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.model.SkillActivation;

import java.util.List;
import java.util.Objects;

/** 在 Agent 模型调用前将已决策的 ALWAYS_ON Skill 写入唯一 ExecutionState。 */
public final class SkillHook {

    public void beforeAgent(ExecutionState state, List<SkillActivation> activations) {
        Objects.requireNonNull(state, "ExecutionState不能为空");
        List.copyOf(Objects.requireNonNullElseGet(activations, List::of))
                .forEach(state::activateSkill);
    }
}
