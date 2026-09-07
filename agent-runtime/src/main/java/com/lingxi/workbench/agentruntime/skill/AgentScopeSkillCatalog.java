package com.lingxi.workbench.agentruntime.skill;

import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import io.agentscope.core.skill.SkillFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 Application 已校验的发布 Skill 快照投影为 AgentScope Repository 和 Filter。
 *
 * <p>该对象不执行模型路由，也不保存运行期激活状态；它只声明本轮哪些冻结 Skill 可以被
 * AgentScope 加载，以及哪些确定性 Skill 需要直接放入 AgentScope 的 Skill 元数据。</p>
 */
public final class AgentScopeSkillCatalog {

    private final AgentScopeSkillRepo repository;
    private final SkillFilter filter;
    private final List<AgentSkillBindingSnapshot> eagerBindings;
    private final Map<String, AgentSkillBindingSnapshot> modelRoutedBindingsBySkillId;

    private AgentScopeSkillCatalog(AgentScopeSkillRepo repository, SkillFilter filter,
                                            List<AgentSkillBindingSnapshot> eagerBindings,
                                            Map<String, AgentSkillBindingSnapshot> modelRoutedBindingsBySkillId) {
        this.repository = repository;
        this.filter = filter;
        this.eagerBindings = List.copyOf(eagerBindings);
        this.modelRoutedBindingsBySkillId = Map.copyOf(modelRoutedBindingsBySkillId);
    }

    public static AgentScopeSkillCatalog from(SkillRuntimeCatalog catalog, boolean modelRoutingAllowed) {
        SkillRuntimeCatalog frozenCatalog = Objects.requireNonNullElseGet(catalog, SkillRuntimeCatalog::empty);
        Set<String> explicitCodes = Set.copyOf(frozenCatalog.explicitSkillCodes());
        List<AgentSkillBindingSnapshot> bindings = frozenCatalog.bindings();
        AgentScopeSkillRepo repository = new AgentScopeSkillRepo(bindings,
                binding -> shouldEagerlyExpose(binding, explicitCodes));
        List<AgentSkillBindingSnapshot> eagerBindings = bindings.stream()
                .filter(binding -> shouldEagerlyExpose(binding, explicitCodes))
                .toList();
        List<String> visibleSkillIds = bindings.stream()
                .filter(binding -> isVisible(binding, explicitCodes, modelRoutingAllowed))
                .map(repository::skillIdOf)
                .toList();
        Map<String, AgentSkillBindingSnapshot> modelRoutedBindings = bindings.stream()
                .filter(binding -> binding.getSkill().getActivationMode() == SkillActivationMode.MODEL_ROUTED)
                .collect(Collectors.toUnmodifiableMap(repository::skillIdOf, binding -> binding));
        SkillFilter filter = visibleSkillIds.isEmpty()
                ? SkillFilter.none()
                : SkillFilter.only(visibleSkillIds.toArray(String[]::new));
        return new AgentScopeSkillCatalog(repository, filter, eagerBindings, modelRoutedBindings);
    }

    public AgentScopeSkillRepo repository() {
        return repository;
    }

    public SkillFilter filter() {
        return filter;
    }

    public List<AgentSkillBindingSnapshot> eagerBindings() {
        return eagerBindings;
    }

    public boolean hasVisibleSkills() {
        return repository.getAllSkills().stream().anyMatch(skill -> filter.isAllowed(skill.getSkillId()));
    }

    public AgentSkillBindingSnapshot modelRoutedBinding(String skillId) {
        return modelRoutedBindingsBySkillId.get(skillId);
    }

    public SkillActivationReason eagerReason(AgentSkillBindingSnapshot binding) {
        return switch (binding.getSkill().getActivationMode()) {
            case ALWAYS_ON -> SkillActivationReason.ALWAYS_ON;
            case EXPLICIT_ONLY -> SkillActivationReason.EXPLICIT;
            case MODEL_ROUTED -> throw new IllegalArgumentException("MODEL_ROUTED Skill不能预加载");
        };
    }

    private static boolean isVisible(AgentSkillBindingSnapshot binding, Set<String> explicitCodes,
                                     boolean modelRoutingAllowed) {
        return switch (binding.getSkill().getActivationMode()) {
            case ALWAYS_ON -> true;
            case EXPLICIT_ONLY -> explicitCodes.contains(binding.getSkill().getCode());
            case MODEL_ROUTED -> modelRoutingAllowed;
        };
    }

    private static boolean shouldEagerlyExpose(AgentSkillBindingSnapshot binding, Set<String> explicitCodes) {
        return binding.getSkill().getActivationMode() == SkillActivationMode.ALWAYS_ON
                || binding.getSkill().getActivationMode() == SkillActivationMode.EXPLICIT_ONLY
                && explicitCodes.contains(binding.getSkill().getCode());
    }
}
