package com.lingxi.workbench.application.runtime.service.skill;

import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeSkillEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeSkillVersionEntity;
import com.lingxi.workbench.application.runtime.repo.SkillRuntimeRepo;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.skill.service.SkillSnapshotChecksum;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillRuntimeServiceTest {

    private SkillRuntimeRepo repo;
    private SkillSnapshotChecksum checksum;
    private TokenCountEstimator tokenEstimator;
    private SkillRuntimeService service;

    @BeforeEach
    void setUp() {
        repo = mock(SkillRuntimeRepo.class);
        checksum = new SkillSnapshotChecksum();
        tokenEstimator = mock(TokenCountEstimator.class);
        when(tokenEstimator.estimate(anyString())).thenReturn(20);
        service = new SkillRuntimeService(repo, checksum, tokenEstimator);
    }

    @Test
    void shouldResolveFrozenAlwaysOnSkill() {
        AgentSkillBindingSnapshot binding = binding();
        prepareRuntime(binding, null);

        SkillRuntimeCatalog catalog = service.load(agent(binding), budget(100), List.of());

        assertEquals(1, catalog.bindings().size());
        assertEquals(List.of(), catalog.explicitSkillCodes());
    }

    @Test
    void shouldAcceptServerValidatedExplicitSkill() {
        AgentSkillBindingSnapshot binding = binding(SkillActivationMode.EXPLICIT_ONLY);
        prepareRuntime(binding, null);

        SkillRuntimeCatalog catalog = service.load(agent(binding), budget(100), List.of("fact_check"));

        assertEquals(List.of("fact_check"), catalog.explicitSkillCodes());
    }

    @Test
    void shouldKeepModelRoutedSkillInFrozenCatalogWithoutActivatingIt() {
        AgentSkillBindingSnapshot binding = binding(SkillActivationMode.MODEL_ROUTED);
        prepareRuntime(binding, null);

        SkillRuntimeCatalog catalog = service.load(agent(binding), budget(100), List.of());

        assertEquals(SkillActivationMode.MODEL_ROUTED,
                catalog.bindings().getFirst().getSkill().getActivationMode());
    }

    @Test
    void shouldRejectExplicitCodeOutsideExplicitOnlyCatalog() {
        AgentSkillBindingSnapshot binding = binding();
        prepareRuntime(binding, null);

        assertThrows(BusinessException.class,
                () -> service.load(agent(binding), budget(100), List.of("fact_check")));
    }

    @Test
    void shouldRejectRevokedSkillVersion() {
        AgentSkillBindingSnapshot binding = binding();
        prepareRuntime(binding, OffsetDateTime.now());

        assertThrows(BusinessException.class,
                () -> service.load(agent(binding), budget(100), List.of()));
    }

    @Test
    void shouldRejectSkillInstructionsOutsideCapabilityBudget() {
        AgentSkillBindingSnapshot binding = binding();
        prepareRuntime(binding, null);

        assertThrows(BusinessException.class,
                () -> service.load(agent(binding), budget(10), List.of()));
    }

    @Test
    void shouldRejectTamperedToolIntersection() {
        AgentSkillBindingSnapshot binding = binding();
        binding.setEffectiveToolCodes(List.of("calendar_write"));
        binding.setDeniedToolCodes(List.of("knowledge_search"));
        prepareRuntime(binding, null);

        assertThrows(BusinessException.class,
                () -> service.load(agent(binding), budget(100), List.of()));
    }

    @Test
    void shouldRejectMissingSkillIdBeforeRepositoryLookup() {
        AgentSkillBindingSnapshot binding = binding();
        binding.setSkillId(null);

        assertThrows(BusinessException.class,
                () -> service.load(agent(binding), budget(100), List.of()));
    }

    @Test
    void shouldRejectCatalogAboveBoundedRoutingThreshold() {
        List<AgentSkillBindingSnapshot> bindings = IntStream.rangeClosed(1, 4)
                .mapToObj(index -> binding(index, "skill_" + index))
                .toList();
        AgentRuntimeDefinition agent = AgentRuntimeDefinition.builder()
                .toolCodes(List.of("knowledge_search"))
                .skills(bindings)
                .build();

        assertThrows(BusinessException.class,
                () -> service.load(agent, budget(1000), List.of()));
    }

    private void prepareRuntime(AgentSkillBindingSnapshot binding, OffsetDateTime revokedAt) {
        RuntimeSkillEntity skill = new RuntimeSkillEntity();
        skill.setId(binding.getSkillId());
        skill.setCode(binding.getSkill().getCode());
        skill.setStatus(SkillLifecycleStatus.ACTIVE);
        skill.setSecurityStatus(SkillSecurityStatus.NORMAL);
        RuntimeSkillVersionEntity version = new RuntimeSkillVersionEntity();
        version.setId(binding.getSkillVersionId());
        version.setSkillId(binding.getSkillId());
        version.setVersionNo(binding.getVersionNo());
        version.setSchemaVersion(1);
        version.setSnapshot(binding.getSkill());
        version.setSnapshotChecksum(binding.getChecksum());
        version.setRevokedAt(revokedAt);
        when(repo.findSkills(List.of(binding.getSkillId()))).thenReturn(List.of(skill));
        when(repo.findVersions(List.of(binding.getSkillVersionId()))).thenReturn(List.of(version));
    }

    private AgentRuntimeDefinition agent(AgentSkillBindingSnapshot binding) {
        return AgentRuntimeDefinition.builder()
                .toolCodes(List.of("knowledge_search"))
                .skills(List.of(binding))
                .build();
    }

    private AgentSkillBindingSnapshot binding() {
        return binding(SkillActivationMode.ALWAYS_ON);
    }

    private AgentSkillBindingSnapshot binding(SkillActivationMode mode) {
        SkillVersionSnapshot snapshot = SkillVersionSnapshot.builder()
                .schemaVersion(1)
                .code("fact_check")
                .name("事实核验")
                .description("核验事实来源")
                .instructions("先核验事实，再给出结论。")
                .activationMode(mode)
                .requestedToolCodes(List.of("knowledge_search", "calendar_write"))
                .build();
        return AgentSkillBindingSnapshot.builder()
                .skillId(1L)
                .skillVersionId(11L)
                .versionNo(1)
                .checksum(checksum.calculate(snapshot))
                .skill(snapshot)
                .effectiveToolCodes(List.of("knowledge_search"))
                .deniedToolCodes(List.of("calendar_write"))
                .build();
    }

    private AgentSkillBindingSnapshot binding(int index, String code) {
        SkillVersionSnapshot snapshot = SkillVersionSnapshot.builder()
                .schemaVersion(1)
                .code(code)
                .name("Skill " + index)
                .description("受限目录")
                .instructions("执行受限能力。")
                .activationMode(SkillActivationMode.MODEL_ROUTED)
                .requestedToolCodes(List.of())
                .build();
        return AgentSkillBindingSnapshot.builder()
                .skillId((long) index)
                .skillVersionId((long) index + 10)
                .versionNo(1)
                .checksum(checksum.calculate(snapshot))
                .skill(snapshot)
                .effectiveToolCodes(List.of())
                .deniedToolCodes(List.of())
                .build();
    }

    private static ContextBudgetPolicy budget(int maxCapabilityTokens) {
        return ContextBudgetPolicy.builder().maxCapabilityTokens(maxCapabilityTokens).build();
    }
}
