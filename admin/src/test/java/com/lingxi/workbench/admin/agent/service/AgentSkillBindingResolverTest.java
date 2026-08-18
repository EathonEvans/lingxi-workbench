package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import com.lingxi.workbench.admin.skill.model.entity.SkillEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillVersionEntity;
import com.lingxi.workbench.admin.skill.repo.SkillRepo;
import com.lingxi.workbench.admin.skill.repo.SkillVersionRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.skill.service.SkillSnapshotChecksum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSkillBindingResolverTest {

    private SkillRepo skillRepo;
    private SkillVersionRepo versionRepo;
    private SkillSnapshotChecksum checksum;
    private AgentSkillBindingResolver resolver;

    @BeforeEach
    void setUp() {
        skillRepo = mock(SkillRepo.class);
        versionRepo = mock(SkillVersionRepo.class);
        checksum = new SkillSnapshotChecksum();
        resolver = new AgentSkillBindingResolver(skillRepo, versionRepo, checksum);
    }

    @Test
    void shouldFreezePublishedVersionAndIntersectAgentTools() {
        SkillEntity skill = skill(1L, 11L);
        SkillVersionEntity version = version(11L, skillSnapshot());
        when(skillRepo.findAllByIds(List.of(1L))).thenReturn(List.of(skill));
        when(versionRepo.findAllByIds(List.of(11L))).thenReturn(List.of(version));

        List<AgentSkillBindingSnapshot> bindings = resolver.resolve(
                List.of(1L), List.of("knowledge_search"));

        assertEquals(List.of("knowledge_search"), bindings.getFirst().getEffectiveToolCodes());
        assertEquals(List.of("calendar_write"), bindings.getFirst().getDeniedToolCodes());
        assertEquals(11L, bindings.getFirst().getSkillVersionId());
    }

    @Test
    void shouldAllowHistoricalFrozenVersionWhenCurrentPublishedVersionChanged() {
        SkillEntity skill = skill(1L, 12L);
        SkillVersionEntity historical = version(11L, skillSnapshot());
        AgentSkillBindingSnapshot binding = binding(historical);
        when(skillRepo.findAllByIds(List.of(1L))).thenReturn(List.of(skill));
        when(versionRepo.findAllByIds(List.of(11L))).thenReturn(List.of(historical));

        resolver.validateFrozen(List.of(binding), List.of("knowledge_search"));
    }

    @Test
    void shouldRejectRevokedFrozenVersion() {
        SkillEntity skill = skill(1L, 11L);
        SkillVersionEntity version = version(11L, skillSnapshot());
        version.setRevokedAt(OffsetDateTime.now());
        when(skillRepo.findAllByIds(List.of(1L))).thenReturn(List.of(skill));
        when(versionRepo.findAllByIds(List.of(11L))).thenReturn(List.of(version));

        assertThrows(BusinessException.class,
                () -> resolver.validateFrozen(List.of(binding(version)), List.of("knowledge_search")));
    }

    @Test
    void shouldRejectTamperedToolIntersection() {
        SkillEntity skill = skill(1L, 11L);
        SkillVersionEntity version = version(11L, skillSnapshot());
        AgentSkillBindingSnapshot binding = binding(version);
        binding.setEffectiveToolCodes(List.of("calendar_write"));
        binding.setDeniedToolCodes(List.of("knowledge_search"));
        when(skillRepo.findAllByIds(List.of(1L))).thenReturn(List.of(skill));
        when(versionRepo.findAllByIds(List.of(11L))).thenReturn(List.of(version));

        assertThrows(BusinessException.class,
                () -> resolver.validateFrozen(List.of(binding), List.of("knowledge_search")));
    }

    @Test
    void shouldRejectMissingFrozenSkillSnapshot() {
        assertThrows(BusinessException.class,
                () -> resolver.validateFrozen(null, List.of("knowledge_search")));
    }

    @Test
    void shouldRejectMissingFrozenToolSnapshot() {
        assertThrows(BusinessException.class,
                () -> resolver.validateFrozen(List.of(), null));
    }

    private SkillEntity skill(Long id, Long publishedVersionId) {
        SkillEntity skill = new SkillEntity();
        skill.setId(id);
        skill.setCode("fact_check");
        skill.setName("事实核验");
        skill.setSourceType(SkillSourceType.SYSTEM_PRESET);
        skill.setVisibility(SkillVisibility.SYSTEM);
        skill.setStatus(SkillLifecycleStatus.ACTIVE);
        skill.setSecurityStatus(SkillSecurityStatus.NORMAL);
        skill.setPublishedVersionId(publishedVersionId);
        return skill;
    }

    private SkillVersionEntity version(Long id, SkillVersionSnapshot snapshot) {
        SkillVersionEntity version = new SkillVersionEntity();
        version.setId(id);
        version.setSkillId(1L);
        version.setVersionNo(1);
        version.setSchemaVersion(1);
        version.setSnapshot(snapshot);
        version.setSnapshotChecksum(checksum.calculate(snapshot));
        return version;
    }

    private AgentSkillBindingSnapshot binding(SkillVersionEntity version) {
        return AgentSkillBindingSnapshot.builder()
                .skillId(1L)
                .skillVersionId(version.getId())
                .versionNo(version.getVersionNo())
                .checksum(version.getSnapshotChecksum())
                .skill(version.getSnapshot())
                .effectiveToolCodes(List.of("knowledge_search"))
                .deniedToolCodes(List.of("calendar_write"))
                .build();
    }

    private static SkillVersionSnapshot skillSnapshot() {
        return SkillVersionSnapshot.builder()
                .schemaVersion(1)
                .code("fact_check")
                .name("事实核验")
                .description("核验回答依据")
                .instructions("先核验事实，再给出结论。")
                .activationMode(SkillActivationMode.ALWAYS_ON)
                .requestedToolCodes(List.of("knowledge_search", "calendar_write"))
                .build();
    }
}
