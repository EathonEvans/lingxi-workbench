package com.lingxi.workbench.application.runtime.service.skill;

import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeSkillEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeSkillVersionEntity;
import com.lingxi.workbench.application.runtime.repo.SkillRuntimeRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.service.SkillSnapshotChecksum;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lingxi.workbench.infra.ai.skill.common.constants.SkillSchemaConstants.SKILL_SCHEMA_VERSION;

/**
 * 执行前校验 AgentVersion 锁定的 SkillVersion，并向 Runtime 提供冻结目录与显式选择。
 */
@Service
@RequiredArgsConstructor
public class SkillRuntimeService {

    private static final int MAX_SKILLS = 3;

    private final SkillRuntimeRepo repo;
    private final SkillSnapshotChecksum snapshotChecksum;
    private final TokenCountEstimator tokenCountEstimator;

    public SkillRuntimeCatalog load(AgentRuntimeDefinition agent, ContextBudgetPolicy budget,
                                    List<String> explicitSkillCodes) {
        List<AgentSkillBindingSnapshot> bindings = agent.resolveSkills();
        if (bindings.size() > MAX_SKILLS) {
            throw invalid("AgentVersion锁定的Skill数量超过平台上限");
        }
        List<String> explicitCodes = List.copyOf(
                Objects.requireNonNullElseGet(explicitSkillCodes, List::of));
        requireUnique(explicitCodes, "显式Skill不能重复选择");
        if (bindings.isEmpty()) {
            if (!explicitCodes.isEmpty()) {
                throw invalid("显式Skill不在当前AgentVersion冻结目录中");
            }
            return SkillRuntimeCatalog.empty();
        }
        if (bindings.stream().anyMatch(binding -> Objects.isNull(binding) || Objects.isNull(binding.getSkill()))) {
            throw invalid("AgentVersion包含不完整的Skill绑定");
        }

        List<Long> skillIds = bindings.stream().map(AgentSkillBindingSnapshot::getSkillId).toList();
        List<Long> versionIds = bindings.stream().map(AgentSkillBindingSnapshot::getSkillVersionId).toList();
        List<String> skillCodes = bindings.stream().map(binding -> binding.getSkill().getCode()).toList();
        requirePresent(skillIds, "AgentVersion存在无效Skill绑定");
        requirePresent(versionIds, "AgentVersion存在无效SkillVersion绑定");
        requireUnique(skillIds, "AgentVersion存在重复Skill绑定");
        requireUnique(versionIds, "AgentVersion存在重复SkillVersion绑定");
        requireUnique(skillCodes, "AgentVersion存在重复Skill编码");
        Map<Long, RuntimeSkillEntity> skills = repo.findSkills(skillIds).stream()
                .collect(Collectors.toMap(RuntimeSkillEntity::getId, Function.identity()));
        // 查询 skill 版本
        Map<Long, RuntimeSkillVersionEntity> versions = repo.findVersions(versionIds).stream()
                .collect(Collectors.toMap(RuntimeSkillVersionEntity::getId, Function.identity()));
        Set<String> agentTools = new HashSet<>(agent.resolveToolCodes());
        Map<String, AgentSkillBindingSnapshot> bindingsByCode = bindings.stream()
                .collect(Collectors.toMap(binding -> binding.getSkill().getCode(), Function.identity()));
        int totalTokens = 0;
        for (AgentSkillBindingSnapshot binding : bindings) {
            if (Objects.isNull(binding.getSkill().getActivationMode())) {
                throw invalid("AgentVersion包含缺少激活模式的Skill绑定");
            }
            RuntimeSkillEntity skill = skills.get(binding.getSkillId());
            RuntimeSkillVersionEntity version = versions.get(binding.getSkillVersionId());
            validateBinding(binding, skill, version, agentTools);
            totalTokens += tokenCountEstimator.estimate(binding.getSkill().getCode())
                    + tokenCountEstimator.estimate(binding.getSkill().getName())
                    + tokenCountEstimator.estimate(Objects.requireNonNullElse(
                    binding.getSkill().getDescription(), ""))
                    + tokenCountEstimator.estimate(binding.getSkill().getInstructions());
        }
        if (totalTokens > budget.getMaxCapabilityTokens()) {
            throw invalid("Skill元数据与指令超过Application能力描述Token预算");
        }
        for (String explicitCode : explicitCodes) {
            if (explicitCode == null || explicitCode.isBlank()) {
                throw invalid("显式Skill编码不能为空");
            }
            AgentSkillBindingSnapshot binding = bindingsByCode.get(explicitCode);
            if (binding == null
                    || binding.getSkill().getActivationMode() != SkillActivationMode.EXPLICIT_ONLY) {
                throw invalid("显式Skill不在当前AgentVersion的EXPLICIT_ONLY目录中：" + explicitCode);
            }
        }
        return new SkillRuntimeCatalog(bindings, explicitCodes);
    }

    private void validateBinding(AgentSkillBindingSnapshot binding, RuntimeSkillEntity skill,
                                 RuntimeSkillVersionEntity version, Set<String> agentTools) {
        if (Objects.isNull(skill)
                || skill.getStatus() != SkillLifecycleStatus.ACTIVE
                || skill.getSecurityStatus() != SkillSecurityStatus.NORMAL
                || !Objects.equals(skill.getCode(), binding.getSkill().getCode())) {
            throw invalid("Skill已停用、撤销或安全状态不允许执行：" + binding.getSkill().getCode());
        }
        if (Objects.isNull(version)
                || Objects.nonNull(version.getRevokedAt())
                || !Objects.equals(version.getSkillId(), binding.getSkillId())
                || !Objects.equals(version.getVersionNo(), binding.getVersionNo())
                || !Objects.equals(version.getSchemaVersion(), SKILL_SCHEMA_VERSION)
                || Objects.isNull(version.getSnapshot())
                || !Objects.equals(version.getSnapshotChecksum(), binding.getChecksum())
                || !Objects.equals(snapshotChecksum.calculate(version.getSnapshot()), binding.getChecksum())
                || !Objects.equals(snapshotChecksum.calculate(binding.getSkill()), binding.getChecksum())) {
            throw invalid("AgentVersion锁定的SkillVersion完整性校验失败：" + binding.getSkill().getCode());
        }
        List<String> effectiveTools = Objects.requireNonNullElse(binding.getEffectiveToolCodes(), List.of());
        List<String> deniedTools = Objects.requireNonNullElse(binding.getDeniedToolCodes(), List.of());
        List<String> requestedTools = Objects.requireNonNullElse(binding.getSkill().getRequestedToolCodes(), List.of());
        List<String> expectedEffective = requestedTools.stream().filter(agentTools::contains).toList();
        List<String> expectedDenied = requestedTools.stream().filter(code -> !agentTools.contains(code)).toList();
        if (!expectedEffective.equals(effectiveTools) || !expectedDenied.equals(deniedTools)) {
            throw invalid("Skill工具权限交集与AgentVersion快照不一致：" + binding.getSkill().getCode());
        }
    }

    private static <T> void requireUnique(List<T> values, String message) {
        if (new HashSet<>(values).size() != values.size()) {
            throw invalid(message);
        }
    }

    private static <T> void requirePresent(List<T> values, String message) {
        if (values.stream().anyMatch(Objects::isNull)) {
            throw invalid(message);
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}
