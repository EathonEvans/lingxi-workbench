package com.lingxi.workbench.agentruntime.skill;

import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** 只读装载本轮 AgentVersion 冻结 SkillVersion 的 AgentScope Repository。 */
public final class AgentScopeSkillRepo implements AgentSkillRepository {

    private static final String SOURCE_PREFIX = "lingxi-published-v";

    private final Map<String, AgentSkill> skillsByName;
    private final Map<String, String> skillIdsByCode;

    AgentScopeSkillRepo(List<AgentSkillBindingSnapshot> bindings,
                                       Predicate<AgentSkillBindingSnapshot> eagerBinding) {
        Map<String, AgentSkill> skills = new LinkedHashMap<>();
        Map<String, String> ids = new LinkedHashMap<>();
        for (AgentSkillBindingSnapshot binding : List.copyOf(bindings)) {
            String code = binding.getSkill().getCode();
            String source = SOURCE_PREFIX + binding.getSkillVersionId();
            AgentSkill skill = new AgentSkill(metadata(binding, eagerBinding.test(binding)),
                    binding.getSkill().getInstructions(), Map.of(), source);
            if (skills.putIfAbsent(code, skill) != null) {
                throw new IllegalArgumentException("发布Skill目录存在重复编码：" + code);
            }
            ids.put(code, skill.getSkillId());
        }
        this.skillsByName = Map.copyOf(skills);
        this.skillIdsByCode = Map.copyOf(ids);
    }

    @Override
    public AgentSkill getSkill(String name) {
        AgentSkill skill = skillsByName.get(name);
        if (skill == null) {
            throw new IllegalArgumentException("发布Skill不存在：" + name);
        }
        return skill;
    }

    @Override
    public List<String> getAllSkillNames() {
        return List.copyOf(skillsByName.keySet());
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return List.copyOf(skillsByName.values());
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean force) {
        return false;
    }

    @Override
    public boolean delete(String skillName) {
        return false;
    }

    @Override
    public boolean skillExists(String skillName) {
        return skillsByName.containsKey(skillName);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("lingxi-published", "agent-version-snapshot", false);
    }

    @Override
    public String getSource() {
        return "lingxi-published";
    }

    @Override
    public void setWriteable(boolean writeable) {
        // 发布快照不可被 AgentScope 运行期修改。
    }

    @Override
    public boolean isWriteable() {
        return false;
    }

    String skillIdOf(AgentSkillBindingSnapshot binding) {
        String code = Objects.requireNonNull(binding, "Skill绑定不能为空").getSkill().getCode();
        String skillId = skillIdsByCode.get(code);
        if (skillId == null) {
            throw new IllegalArgumentException("Skill绑定不属于当前发布目录：" + code);
        }
        return skillId;
    }

    private static Map<String, Object> metadata(AgentSkillBindingSnapshot binding, boolean eager) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", binding.getSkill().getCode());
        String description = binding.getSkill().getDescription();
        metadata.put("description", description == null || description.isBlank()
                ? binding.getSkill().getName() : description);
        if (eager) {
            // ALWAYS_ON 与已显式选择的 Skill 由 AgentScope 自己的目录提示注入，不再拼接 Runtime Prompt。
            metadata.put("instructions", binding.getSkill().getInstructions());
        }
        return metadata;
    }
}
