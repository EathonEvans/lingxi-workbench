package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.admin.agent.model.vo.AgentSkillOptionVO;
import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import com.lingxi.workbench.admin.skill.model.entity.SkillEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillVersionEntity;
import com.lingxi.workbench.admin.skill.repo.SkillRepo;
import com.lingxi.workbench.admin.skill.repo.SkillVersionRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.skill.service.SkillSnapshotChecksum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lingxi.workbench.infra.ai.skill.common.constants.SkillSchemaConstants.SKILL_SCHEMA_VERSION;

/** 解析 Agent 草稿中的 Skill，并在发布边界冻结精确 SkillVersion。 */
@Component
@RequiredArgsConstructor
public class AgentSkillBindingResolver {

    private static final int MAX_SKILLS = 3;

    private final SkillRepo skillRepo;
    private final SkillVersionRepo versionRepo;
    private final SkillSnapshotChecksum snapshotChecksum;

    /** 返回当前允许 Agent 选择且能够立即执行的平台 Skill。 */
    public List<AgentSkillOptionVO> options() {
        List<SkillEntity> skills = skillRepo.findAllAgentBindable();
        List<Long> versionIds = skills.stream().map(SkillEntity::getPublishedVersionId).toList();
        Map<Long, SkillVersionEntity> versions = versionRepo.findAllByIds(versionIds).stream()
                .collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));
        return skills.stream()
                .map(skill -> toOption(skill, versions.get(skill.getPublishedVersionId())))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 校验选择结果，并按草稿顺序构建不可变绑定快照。 */
    public List<AgentSkillBindingSnapshot> resolve(List<Long> skillIds, List<String> agentToolCodes) {
        List<Long> requestedIds = Objects.requireNonNullElse(skillIds, List.of());
        if (requestedIds.size() > MAX_SKILLS) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "单个Agent最多绑定3个自动启用Skill");
        }
        if (new HashSet<>(requestedIds).size() != requestedIds.size()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Skill不能重复绑定");
        }
        if (requestedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, SkillEntity> skills = skillRepo.findAllByIds(requestedIds).stream()
                .collect(Collectors.toMap(SkillEntity::getId, Function.identity()));
        List<Long> versionIds = skills.values().stream()
                .map(SkillEntity::getPublishedVersionId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SkillVersionEntity> versions = versionRepo.findAllByIds(versionIds).stream()
                .collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));
        Set<String> toolWhitelist = new HashSet<>(Objects.requireNonNullElse(agentToolCodes, List.of()));
        Map<Long, AgentSkillBindingSnapshot> bindings = new LinkedHashMap<>();
        for (Long skillId : requestedIds) {
            SkillEntity skill = skills.get(skillId);
            SkillVersionEntity version = Objects.isNull(skill) ? null : versions.get(skill.getPublishedVersionId());
            requireBindable(skill, version);
            List<String> requestedTools = Objects.requireNonNullElse(version.getSnapshot().getRequestedToolCodes(), List.of());
            List<String> effectiveTools = requestedTools.stream().filter(toolWhitelist::contains).toList();
            List<String> deniedTools = requestedTools.stream().filter(code -> !toolWhitelist.contains(code)).toList();
            bindings.put(skillId, AgentSkillBindingSnapshot.builder()
                    .skillId(skill.getId())
                    .skillVersionId(version.getId())
                    .versionNo(version.getVersionNo())
                    .checksum(version.getSnapshotChecksum())
                    .skill(version.getSnapshot())
                    .effectiveToolCodes(List.copyOf(effectiveTools))
                    .deniedToolCodes(List.copyOf(deniedTools))
                    .build());
        }
        return List.copyOf(bindings.values());
    }

    /**
     * 切换历史 AgentVersion 前重新校验冻结的 SkillVersion。
     *
     * <p>历史版本仍使用发布时锁定的 SkillVersion，不追随 Skill 当前发布版本；但 Skill 停用、
     * 安全封禁或指定版本撤销属于运行时熔断条件，不能通过切换 AgentVersion 绕过。</p>
     */
    public void validateFrozen(List<AgentSkillBindingSnapshot> bindings, List<String> agentToolCodes) {
        if (Objects.isNull(bindings)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少Skill绑定快照");
        }
        if (Objects.isNull(agentToolCodes)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion缺少工具权限快照");
        }
        List<AgentSkillBindingSnapshot> frozenBindings = List.copyOf(bindings);
        if (frozenBindings.size() > MAX_SKILLS) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion锁定的Skill数量超过平台上限");
        }
        if (frozenBindings.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion存在不完整的Skill绑定");
        }
        List<Long> skillIds = frozenBindings.stream().map(AgentSkillBindingSnapshot::getSkillId).toList();
        List<Long> versionIds = frozenBindings.stream().map(AgentSkillBindingSnapshot::getSkillVersionId).toList();
        if (skillIds.stream().anyMatch(Objects::isNull) || new HashSet<>(skillIds).size() != skillIds.size()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion存在无效或重复Skill绑定");
        }
        if (versionIds.stream().anyMatch(Objects::isNull) || new HashSet<>(versionIds).size() != versionIds.size()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion存在无效或重复SkillVersion绑定");
        }
        if (frozenBindings.isEmpty()) {
            return;
        }

        Map<Long, SkillEntity> skills = skillRepo.findAllByIds(skillIds).stream()
                .collect(Collectors.toMap(SkillEntity::getId, Function.identity()));
        Map<Long, SkillVersionEntity> versions = versionRepo.findAllByIds(versionIds).stream()
                .collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity()));
        Set<String> toolWhitelist = new HashSet<>(agentToolCodes);
        for (AgentSkillBindingSnapshot binding : frozenBindings) {
            requireFrozenExecutable(binding, skills.get(binding.getSkillId()),
                    versions.get(binding.getSkillVersionId()), toolWhitelist);
        }
    }

    private AgentSkillOptionVO toOption(SkillEntity skill, SkillVersionEntity version) {
        if (!isBindable(skill, version)) {
            return null;
        }
        SkillVersionSnapshot snapshot = version.getSnapshot();
        return AgentSkillOptionVO.builder()
                .id(String.valueOf(skill.getId()))
                .versionId(String.valueOf(version.getId()))
                .versionNo(version.getVersionNo())
                .code(skill.getCode())
                .name(skill.getName())
                .description(skill.getDescription())
                .activationMode(snapshot.getActivationMode())
                .requestedToolCodes(List.copyOf(Objects.requireNonNullElse(snapshot.getRequestedToolCodes(), List.of())))
                .build();
    }

    private void requireBindable(SkillEntity skill, SkillVersionEntity version) {
        if (!isBindable(skill, version)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill不存在、已停用或当前版本不可供Agent绑定");
        }
    }

    private boolean isBindable(SkillEntity skill, SkillVersionEntity version) {
        if (Objects.isNull(skill)
                || skill.getSourceType() != SkillSourceType.SYSTEM_PRESET
                || skill.getVisibility() != SkillVisibility.SYSTEM
                || skill.getStatus() != SkillLifecycleStatus.ACTIVE
                || skill.getSecurityStatus() != SkillSecurityStatus.NORMAL
                || Objects.isNull(version)
                || Objects.nonNull(version.getRevokedAt())
                || !Objects.equals(version.getSkillId(), skill.getId())
                || !Objects.equals(version.getSchemaVersion(), SKILL_SCHEMA_VERSION)
                || Objects.isNull(version.getSnapshot())
                || !Objects.equals(version.getSnapshot().getSchemaVersion(), SKILL_SCHEMA_VERSION)
                || version.getSnapshot().getActivationMode() != SkillActivationMode.ALWAYS_ON
                || !Objects.equals(version.getSnapshot().getCode(), skill.getCode())
                || !Objects.equals(snapshotChecksum.calculate(version.getSnapshot()), version.getSnapshotChecksum())) {
            return false;
        }
        return true;
    }

    private void requireFrozenExecutable(AgentSkillBindingSnapshot binding, SkillEntity skill,
                                         SkillVersionEntity version, Set<String> toolWhitelist) {
        if (Objects.isNull(binding.getSkill())
                || Objects.isNull(skill)
                || skill.getSourceType() != SkillSourceType.SYSTEM_PRESET
                || skill.getVisibility() != SkillVisibility.SYSTEM
                || skill.getStatus() != SkillLifecycleStatus.ACTIVE
                || skill.getSecurityStatus() != SkillSecurityStatus.NORMAL
                || Objects.isNull(version)
                || Objects.nonNull(version.getRevokedAt())
                || !Objects.equals(version.getSkillId(), binding.getSkillId())
                || !Objects.equals(version.getVersionNo(), binding.getVersionNo())
                || !Objects.equals(version.getSchemaVersion(), SKILL_SCHEMA_VERSION)
                || Objects.isNull(version.getSnapshot())
                || version.getSnapshot().getActivationMode() != SkillActivationMode.ALWAYS_ON
                || !Objects.equals(skill.getCode(), binding.getSkill().getCode())
                || !Objects.equals(version.getSnapshotChecksum(), binding.getChecksum())
                || !Objects.equals(snapshotChecksum.calculate(version.getSnapshot()), binding.getChecksum())
                || !Objects.equals(snapshotChecksum.calculate(binding.getSkill()), binding.getChecksum())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "AgentVersion锁定的Skill已停用、撤销或完整性校验失败");
        }
        List<String> requestedTools = Objects.requireNonNullElse(binding.getSkill().getRequestedToolCodes(), List.of());
        List<String> effectiveTools = requestedTools.stream().filter(toolWhitelist::contains).toList();
        List<String> deniedTools = requestedTools.stream().filter(code -> !toolWhitelist.contains(code)).toList();
        if (!effectiveTools.equals(Objects.requireNonNullElse(binding.getEffectiveToolCodes(), List.of()))
                || !deniedTools.equals(Objects.requireNonNullElse(binding.getDeniedToolCodes(), List.of()))) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "AgentVersion锁定的Skill工具权限交集不一致");
        }
    }
}
