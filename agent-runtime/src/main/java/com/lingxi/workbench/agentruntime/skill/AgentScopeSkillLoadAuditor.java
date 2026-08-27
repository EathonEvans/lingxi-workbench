package com.lingxi.workbench.agentruntime.skill;

import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 将 AgentScope 实际成功加载的 MODEL_ROUTED Skill 投影为 Lingxi 审计事件。 */
public final class AgentScopeSkillLoadAuditor implements MiddlewareBase {

    private static final String SKILL_LOAD_TOOL = "load_skill_through_path";

    private final AgentScopePublishedSkillCatalog catalog;
    private final ExecutionTools tools;
    private final ExecutionEvents events;
    private final Set<String> auditedSkillIds = new HashSet<>();

    public AgentScopeSkillLoadAuditor(AgentScopePublishedSkillCatalog catalog, ExecutionTools tools,
                                      ExecutionEvents events) {
        this.catalog = catalog;
        this.tools = tools;
        this.events = events;
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        Map<String, String> requestedSkillIds = requestedSkillIds(input.toolCalls());
        return next.apply(input).doOnNext(event -> {
            if (event instanceof ToolResultEndEvent end && end.getState() == ToolResultState.SUCCESS) {
                recordSuccessfulLoad(end, requestedSkillIds);
            }
        });
    }

    private Map<String, String> requestedSkillIds(List<ToolUseBlock> toolCalls) {
        Map<String, String> skillIds = new HashMap<>();
        for (ToolUseBlock toolCall : toolCalls) {
            if (!SKILL_LOAD_TOOL.equals(toolCall.getName())) {
                continue;
            }
            Object skillId = toolCall.getInput().get("skillId");
            if (skillId instanceof String value && !value.isBlank()) {
                skillIds.put(toolCall.getId(), value);
            }
        }
        return skillIds;
    }

    private void recordSuccessfulLoad(ToolResultEndEvent end, Map<String, String> requestedSkillIds) {
        String skillId = requestedSkillIds.get(end.getToolCallId());
        if (skillId == null || !auditedSkillIds.add(skillId)) {
            return;
        }
        AgentSkillBindingSnapshot binding = catalog.modelRoutedBinding(skillId);
        if (binding != null) {
            tools.validateSkillTools(binding.getEffectiveToolCodes(), binding.getSkill().getCode());
            events.skillActivated(binding, SkillActivationReason.MODEL_ROUTED);
        }
    }
}
