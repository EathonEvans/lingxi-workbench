package com.lingxi.workbench.admin.skill.service.impl;

import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import com.lingxi.workbench.admin.skill.model.config.SkillScanReport;
import com.lingxi.workbench.admin.skill.model.converter.SkillConverter;
import com.lingxi.workbench.admin.skill.model.entity.SkillDraftEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillVersionEntity;
import com.lingxi.workbench.admin.skill.model.request.PublishSkillRequest;
import com.lingxi.workbench.admin.skill.model.request.RevokeSkillVersionRequest;
import com.lingxi.workbench.admin.skill.model.request.SkillPageQuery;
import com.lingxi.workbench.admin.skill.model.vo.SkillValidationResultVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionDetailVO;
import com.lingxi.workbench.admin.skill.repo.SkillDraftRepo;
import com.lingxi.workbench.admin.skill.repo.SkillRepo;
import com.lingxi.workbench.admin.skill.repo.SkillVersionRepo;
import com.lingxi.workbench.admin.skill.service.SkillDraftValidator;
import com.lingxi.workbench.admin.tool.service.ToolService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.skill.service.SkillSnapshotChecksum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillServiceImplTest {

    private SkillRepo skillRepo;
    private SkillDraftRepo draftRepo;
    private SkillVersionRepo versionRepo;
    private SkillDraftValidator draftValidator;
    private SkillSnapshotChecksum snapshotChecksum;
    private AuditService auditService;
    private SkillServiceImpl service;

    @BeforeEach
    void setUp() {
        skillRepo = mock(SkillRepo.class);
        draftRepo = mock(SkillDraftRepo.class);
        versionRepo = mock(SkillVersionRepo.class);
        draftValidator = mock(SkillDraftValidator.class);
        snapshotChecksum = mock(SkillSnapshotChecksum.class);
        auditService = mock(AuditService.class);
        service = new SkillServiceImpl(
                skillRepo, draftRepo, versionRepo, draftValidator, snapshotChecksum,
                new SkillConverter(), mock(ToolService.class), auditService);
    }

    @Test
    void shouldPublishValidatedDraftAsImmutableVersion() {
        SkillEntity skill = skill(10L);
        SkillDraftEntity draft = draft(10L);
        PublishSkillRequest request = new PublishSkillRequest();
        request.setExpectedRevision(3L);
        request.setVersionName("首个正式版本");

        when(skillRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(skill));
        when(draftRepo.findBySkillIdForUpdate(10L)).thenReturn(Optional.of(draft));
        when(draftValidator.validate(skill, draft)).thenReturn(new SkillValidationResultVO(true, List.of()));
        when(draftValidator.scanReport(draft.getInstructions())).thenReturn(new SkillScanReport("v1", List.of()));
        when(snapshotChecksum.calculate(any())).thenReturn("checksum-v1");
        when(versionRepo.existsByChecksum(10L, "checksum-v1")).thenReturn(false);
        when(versionRepo.nextVersionNo(10L)).thenReturn(1);
        doAnswer(invocation -> {
            SkillVersionEntity version = invocation.getArgument(0);
            version.setId(101L);
            return null;
        }).when(versionRepo).insert(any(SkillVersionEntity.class));

        SkillVersionDetailVO result = service.publish(10L, request, 7L);

        assertEquals("101", result.getId());
        assertEquals(1, result.getVersionNo());
        assertEquals("首个正式版本", result.getVersionName());
        assertEquals("checksum-v1", result.getSnapshotChecksum());
        assertEquals(101L, skill.getPublishedVersionId());
        verify(skillRepo).save(skill);
        verify(draftRepo).updatePublicationBase(draft.getId(), 101L, 7L, 3L);
        verify(auditService).record(eq("SKILL"), eq("SKILL"), eq(10L), eq(7L), eq("SKILL_PUBLISH"), any());
    }

    @Test
    void shouldRejectPublishWhenDraftRevisionChanged() {
        SkillEntity skill = skill(10L);
        SkillDraftEntity draft = draft(10L);
        PublishSkillRequest request = new PublishSkillRequest();
        request.setExpectedRevision(2L);

        when(skillRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(skill));
        when(draftRepo.findBySkillIdForUpdate(10L)).thenReturn(Optional.of(draft));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.publish(10L, request, 7L));

        assertTrue(exception.getMessage().contains("草稿已变化"));
    }

    @Test
    void shouldLoadOnlyPublishedVersionsForSkillPage() {
        SkillEntity skill = skill(10L);
        skill.setPublishedVersionId(101L);
        SkillVersionEntity version = version(101L, 10L);
        SkillPageQuery query = new SkillPageQuery();

        when(skillRepo.findPage(query, 7L)).thenReturn(new PageResult<>(List.of(skill), 1, 20, 1));
        when(draftRepo.findAllBySkillIds(List.of(10L))).thenReturn(List.of(draft(10L)));
        when(versionRepo.findAllByIds(List.of(101L))).thenReturn(List.of(version));
        when(snapshotChecksum.calculate(version.getSnapshot())).thenReturn("checksum-v1");

        PageResult<SkillVO> result = service.page(query, 7L);

        assertEquals(1, result.total());
        assertTrue(result.records().getFirst().isPublishedVersionReady());
        verify(versionRepo).findAllByIds(List.of(101L));
    }

    @Test
    void shouldMarkPublishedVersionUnavailableWhenChecksumDoesNotMatch() {
        SkillEntity skill = skill(10L);
        skill.setPublishedVersionId(101L);
        SkillVersionEntity version = version(101L, 10L);
        SkillPageQuery query = new SkillPageQuery();

        when(skillRepo.findPage(query, 7L)).thenReturn(new PageResult<>(List.of(skill), 1, 20, 1));
        when(draftRepo.findAllBySkillIds(List.of(10L))).thenReturn(List.of(draft(10L)));
        when(versionRepo.findAllByIds(List.of(101L))).thenReturn(List.of(version));
        when(snapshotChecksum.calculate(version.getSnapshot())).thenReturn("tampered-checksum");

        PageResult<SkillVO> result = service.page(query, 7L);

        assertFalse(result.records().getFirst().isPublishedVersionReady());
    }

    @Test
    void shouldClearPublishedPointerWhenCurrentVersionIsRevoked() {
        SkillEntity skill = skill(10L);
        skill.setPublishedVersionId(101L);
        SkillVersionEntity version = version(101L, 10L);

        when(skillRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(skill));
        when(versionRepo.findBySkillIdAndId(10L, 101L)).thenReturn(Optional.of(version));
        when(versionRepo.revoke(101L, 7L, "安全规则变化")).thenReturn(true);

        service.revokeVersion(10L, 101L, new RevokeSkillVersionRequest("安全规则变化"), 7L);

        assertNull(skill.getPublishedVersionId());
        verify(skillRepo).save(skill);
        verify(auditService).record(eq("SKILL"), eq("SKILL_VERSION"), eq(101L), eq(7L), eq("SKILL_VERSION_REVOKE"), any());
    }

    @Test
    void shouldRejectActivationOfRevokedVersion() {
        SkillEntity skill = skill(10L);
        SkillVersionEntity version = version(101L, 10L);
        version.setRevokedAt(java.time.OffsetDateTime.now());

        when(skillRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(skill));
        when(versionRepo.findBySkillIdAndId(10L, 101L)).thenReturn(Optional.of(version));

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.activateVersion(10L, 101L, 7L));

        assertTrue(exception.getMessage().contains("已撤销"));
    }

    @Test
    void shouldRejectActivationWhenSnapshotChecksumDoesNotMatch() {
        SkillEntity skill = skill(10L);
        SkillVersionEntity version = version(101L, 10L);

        when(skillRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(skill));
        when(versionRepo.findBySkillIdAndId(10L, 101L)).thenReturn(Optional.of(version));
        when(snapshotChecksum.calculate(version.getSnapshot())).thenReturn("tampered-checksum");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.activateVersion(10L, 101L, 7L));

        assertTrue(exception.getMessage().contains("完整性校验失败"));
    }

    @Test
    void shouldActivateVersionWhenSnapshotChecksumMatches() {
        SkillEntity skill = skill(10L);
        SkillVersionEntity version = version(101L, 10L);

        when(skillRepo.findByIdForUpdate(10L)).thenReturn(Optional.of(skill));
        when(versionRepo.findBySkillIdAndId(10L, 101L)).thenReturn(Optional.of(version));
        when(snapshotChecksum.calculate(version.getSnapshot())).thenReturn("checksum-v1");

        service.activateVersion(10L, 101L, 7L);

        assertEquals(101L, skill.getPublishedVersionId());
        verify(skillRepo).save(skill);
    }

    private static SkillEntity skill(Long id) {
        SkillEntity skill = new SkillEntity();
        skill.setId(id);
        skill.setName("事实核验");
        skill.setCode("fact_check");
        skill.setDescription("核对事实并标注来源");
        skill.setSourceType(SkillSourceType.USER_CREATED);
        skill.setVisibility(SkillVisibility.PRIVATE);
        skill.setOwnerUserId(7L);
        skill.setStatus(SkillLifecycleStatus.ACTIVE);
        skill.setSecurityStatus(SkillSecurityStatus.NORMAL);
        return skill;
    }

    private static SkillDraftEntity draft(Long skillId) {
        SkillDraftEntity draft = new SkillDraftEntity();
        draft.setId(20L);
        draft.setSkillId(skillId);
        draft.setSchemaVersion(1);
        draft.setInstructions("先核对事实，再输出结论与来源。");
        draft.setActivationMode(SkillActivationMode.EXPLICIT_ONLY);
        draft.setRequestedToolCodes(List.of("knowledge_search"));
        draft.setRevision(3L);
        draft.setChangeNote("首次发布");
        return draft;
    }

    private static SkillVersionEntity version(Long id, Long skillId) {
        SkillVersionEntity version = new SkillVersionEntity();
        version.setId(id);
        version.setSkillId(skillId);
        version.setVersionNo(1);
        version.setSchemaVersion(1);
        version.setSnapshot(SkillVersionSnapshot.builder()
                .schemaVersion(1)
                .code("fact_check")
                .name("事实核验")
                .instructions("先核对事实，再输出结论与来源。")
                .activationMode(SkillActivationMode.EXPLICIT_ONLY)
                .requestedToolCodes(List.of("knowledge_search"))
                .build());
        version.setSnapshotChecksum("checksum-v1");
        return version;
    }
}
