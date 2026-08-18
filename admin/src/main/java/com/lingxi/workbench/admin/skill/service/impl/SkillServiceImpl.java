package com.lingxi.workbench.admin.skill.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.skill.common.enums.SkillIssueSeverity;
import com.lingxi.workbench.admin.skill.common.enums.SkillSourceType;
import com.lingxi.workbench.admin.skill.common.enums.SkillVisibility;
import com.lingxi.workbench.admin.skill.model.converter.SkillConverter;
import com.lingxi.workbench.admin.skill.model.entity.SkillDraftEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillEntity;
import com.lingxi.workbench.admin.skill.model.entity.SkillVersionEntity;
import com.lingxi.workbench.admin.skill.model.request.CreateSkillRequest;
import com.lingxi.workbench.admin.skill.model.request.PublishSkillRequest;
import com.lingxi.workbench.admin.skill.model.request.RevokeSkillVersionRequest;
import com.lingxi.workbench.admin.skill.model.request.SaveSkillDraftRequest;
import com.lingxi.workbench.admin.skill.model.request.SkillPageQuery;
import com.lingxi.workbench.admin.skill.model.request.UpdateSkillRequest;
import com.lingxi.workbench.admin.skill.model.vo.SkillDetailVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillValidationIssueVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillValidationResultVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionDetailVO;
import com.lingxi.workbench.admin.skill.model.vo.SkillVersionVO;
import com.lingxi.workbench.admin.skill.repo.SkillDraftRepo;
import com.lingxi.workbench.admin.skill.repo.SkillRepo;
import com.lingxi.workbench.admin.skill.repo.SkillVersionRepo;
import com.lingxi.workbench.admin.skill.service.SkillDraftValidator;
import com.lingxi.workbench.admin.skill.service.SkillService;
import com.lingxi.workbench.admin.tool.model.vo.ToolOptionVO;
import com.lingxi.workbench.admin.tool.service.ToolService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillLifecycleStatus;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillSecurityStatus;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.skill.service.SkillSnapshotChecksum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lingxi.workbench.infra.ai.skill.common.constants.SkillSchemaConstants.SKILL_SCHEMA_VERSION;

/** Skill 控制面用例实现。 */
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final SkillRepo skillRepo;
    private final SkillDraftRepo draftRepo;
    private final SkillVersionRepo versionRepo;
    private final SkillDraftValidator draftValidator;
    private final SkillSnapshotChecksum snapshotChecksum;
    private final SkillConverter converter;
    private final ToolService toolService;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<SkillVO> page(SkillPageQuery query, Long operatorId) {
        PageResult<SkillEntity> page = skillRepo.findPage(query, operatorId);
        List<Long> skillIds = page.records().stream().map(SkillEntity::getId).toList();
        List<Long> publishedVersionIds = page.records().stream()
                .map(SkillEntity::getPublishedVersionId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, SkillDraftEntity> drafts = draftRepo.findAllBySkillIds(skillIds).stream()
                .collect(Collectors.toMap(SkillDraftEntity::getSkillId, Function.identity()));
        Map<Long, SkillVersionEntity> versions = versionRepo.findAllByIds(publishedVersionIds).stream()
                .collect(Collectors.toMap(SkillVersionEntity::getId, Function.identity(), (left, right) -> left));
        return page.map(skill -> {
            SkillVersionEntity version = versions.get(skill.getPublishedVersionId());
            return converter.toListVO(skill, version, drafts.get(skill.getId()), isVersionReady(skill, version));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public SkillDetailVO detail(Long id, Long operatorId) {
        SkillEntity skill = requireAccessibleSkill(id, operatorId);
        SkillDraftEntity draft = requireDraft(id);
        SkillVersionEntity publishedVersion = Objects.isNull(skill.getPublishedVersionId())
                ? null
                : versionRepo.findById(skill.getPublishedVersionId()).orElse(null);
        return converter.toDetailVO(skill, publishedVersion, draft, isVersionReady(skill, publishedVersion));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillVersionVO> versions(Long id, Long operatorId) {
        requireAccessibleSkill(id, operatorId);
        return versionRepo.findAll(id).stream().map(converter::toVersionVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SkillVersionDetailVO versionDetail(Long id, Long versionId, Long operatorId) {
        requireAccessibleSkill(id, operatorId);
        return converter.toVersionDetailVO(requireVersion(id, versionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolOptionVO> toolOptions() {
        return toolService.activeOptions();
    }

    @Override
    @Transactional
    public SkillDetailVO create(CreateSkillRequest request, Long operatorId) {
        validateCreatePolicy(request);
        String code = request.getCode().trim().toLowerCase();
        if (skillRepo.existsByCode(code)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill编码已存在");
        }

        SkillEntity skill = new SkillEntity();
        skill.setName(request.getName().trim());
        skill.setCode(code);
        skill.setDescription(trimToNull(request.getDescription()));
        skill.setSourceType(request.getSourceType());
        skill.setVisibility(request.getVisibility());
        skill.setOwnerUserId(request.getSourceType() == SkillSourceType.USER_CREATED ? operatorId : null);
        skill.setStatus(SkillLifecycleStatus.ACTIVE);
        skill.setSecurityStatus(SkillSecurityStatus.NORMAL);
        skill.setCreatedBy(operatorId);
        skill.setUpdatedBy(operatorId);
        skillRepo.save(skill);

        SkillDraftEntity draft = new SkillDraftEntity();
        draft.setSkillId(skill.getId());
        draft.setSchemaVersion(SKILL_SCHEMA_VERSION);
        draft.setInstructions("");
        draft.setActivationMode(SkillActivationMode.EXPLICIT_ONLY);
        draft.setRequestedToolCodes(List.of());
        draft.setRevision(0L);
        draft.setCreatedBy(operatorId);
        draft.setUpdatedBy(operatorId);
        draftRepo.insert(draft);

        auditService.record("SKILL", "SKILL", skill.getId(), operatorId, "SKILL_CREATE", "Skill编码=" + code);
        return detail(skill.getId(), operatorId);
    }

    @Override
    @Transactional
    public SkillDetailVO update(Long id, UpdateSkillRequest request, Long operatorId) {
        SkillEntity skill = requireAccessibleSkill(id, operatorId);
        validateVisibility(skill.getSourceType(), request.getVisibility());
        skill.setName(request.getName().trim());
        skill.setDescription(trimToNull(request.getDescription()));
        skill.setVisibility(request.getVisibility());
        skill.setUpdatedBy(operatorId);
        skillRepo.save(skill);
        auditService.record("SKILL", "SKILL", id, operatorId, "SKILL_UPDATE", "Skill编码=" + skill.getCode());
        return detail(id, operatorId);
    }

    @Override
    @Transactional
    public SkillDetailVO saveDraft(Long id, SaveSkillDraftRequest request, Long operatorId) {
        requireAccessibleSkill(id, operatorId);
        SkillDraftEntity draft = draftRepo.findBySkillIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Skill草稿不存在"));
        if (!Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill草稿已被其他操作修改，请刷新后重试");
        }
        draft.setInstructions(request.getInstructions().trim());
        draft.setActivationMode(request.getActivationMode());
        draft.setRequestedToolCodes(request.getRequestedToolCodes().stream()
                .map(code -> code.trim().toLowerCase())
                .distinct()
                .toList());
        draft.setChangeNote(trimToNull(request.getChangeNote()));
        draft.setUpdatedBy(operatorId);
        if (!draftRepo.updateWithRevision(draft, request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill草稿已被其他操作修改，请刷新后重试");
        }
        auditService.record(
                "SKILL", "SKILL_DRAFT", draft.getId(), operatorId, "SKILL_DRAFT_SAVE",
                "Skill主键=" + id + "，revision=" + (request.getExpectedRevision() + 1));
        return detail(id, operatorId);
    }

    @Override
    @Transactional(readOnly = true)
    public SkillValidationResultVO validate(Long id, Long operatorId) {
        SkillEntity skill = requireAccessibleSkill(id, operatorId);
        return draftValidator.validate(skill, requireDraft(id));
    }

    @Override
    @Transactional
    public SkillVersionDetailVO publish(Long id, PublishSkillRequest request, Long operatorId) {
        SkillEntity skill = skillRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Skill不存在"));
        requireAccessible(skill, operatorId);
        SkillDraftEntity draft = draftRepo.findBySkillIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Skill草稿不存在"));
        if (!Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill草稿已变化，请重新校验后发布");
        }
        SkillValidationResultVO validation = draftValidator.validate(skill, draft);
        if (!validation.valid()) {
            String message = validation.issues().stream()
                    .filter(issue -> issue.severity() == SkillIssueSeverity.ERROR)
                    .map(SkillValidationIssueVO::message)
                    .collect(Collectors.joining("；"));
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Skill发布校验失败：" + message);
        }

        SkillVersionSnapshot snapshot = buildSnapshot(skill, draft);
        String checksum = snapshotChecksum.calculate(snapshot);
        if (versionRepo.existsByChecksum(id, checksum)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前草稿与已发布版本完全相同，无需重复发布");
        }
        int versionNo = versionRepo.nextVersionNo(id);
        SkillVersionEntity version = new SkillVersionEntity();
        version.setSkillId(id);
        version.setVersionNo(versionNo);
        version.setVersionName(versionName(request.getVersionName(), versionNo));
        version.setSchemaVersion(draft.getSchemaVersion());
        version.setSnapshot(snapshot);
        version.setSnapshotChecksum(checksum);
        version.setScanReport(draftValidator.scanReport(draft.getInstructions()));
        version.setChangeNote(draft.getChangeNote());
        version.setPublishedBy(operatorId);
        version.setPublishedAt(OffsetDateTime.now());
        versionRepo.insert(version);

        skill.setPublishedVersionId(version.getId());
        skill.setUpdatedBy(operatorId);
        skillRepo.save(skill);
        draftRepo.updatePublicationBase(draft.getId(), version.getId(), operatorId, request.getExpectedRevision());
        auditService.record(
                "SKILL", "SKILL", id, operatorId, "SKILL_PUBLISH",
                "Skill编码=" + skill.getCode() + "，发布版本=V" + versionNo + "，checksum=" + checksum);
        return converter.toVersionDetailVO(version);
    }

    @Override
    @Transactional
    public void activateVersion(Long id, Long versionId, Long operatorId) {
        SkillEntity skill = skillRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Skill不存在"));
        requireAccessible(skill, operatorId);
        if (skill.getStatus() != SkillLifecycleStatus.ACTIVE || skill.getSecurityStatus() != SkillSecurityStatus.NORMAL) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前Skill状态不能切换版本");
        }
        SkillVersionEntity version = requireVersion(id, versionId);
        if (Objects.nonNull(version.getRevokedAt())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "已撤销的Skill版本不能重新激活");
        }
        if (!Objects.equals(version.getSchemaVersion(), SKILL_SCHEMA_VERSION)
                || Objects.isNull(version.getSnapshot())
                || !Objects.equals(version.getSnapshot().getCode(), skill.getCode())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill历史版本协议或归属不一致");
        }
        String actualChecksum = snapshotChecksum.calculate(version.getSnapshot());
        if (!Objects.equals(actualChecksum, version.getSnapshotChecksum())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill历史版本快照完整性校验失败");
        }
        skill.setPublishedVersionId(versionId);
        skill.setUpdatedBy(operatorId);
        skillRepo.save(skill);
        auditService.record(
                "SKILL", "SKILL", id, operatorId, "SKILL_VERSION_ACTIVATE",
                "Skill编码=" + skill.getCode() + "，生效版本=V" + version.getVersionNo());
    }

    @Override
    @Transactional
    public void revokeVersion(Long id, Long versionId, RevokeSkillVersionRequest request, Long operatorId) {
        SkillEntity skill = skillRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Skill不存在"));
        requireAccessible(skill, operatorId);
        SkillVersionEntity version = requireVersion(id, versionId);
        if (Objects.nonNull(version.getRevokedAt())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill版本已经撤销");
        }
        if (!versionRepo.revoke(versionId, operatorId, request.reason().trim())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Skill版本状态已变化，请刷新后重试");
        }
        if (Objects.equals(skill.getPublishedVersionId(), versionId)) {
            skill.setPublishedVersionId(null);
            skill.setUpdatedBy(operatorId);
            skillRepo.save(skill);
        }
        auditService.record(
                "SKILL", "SKILL_VERSION", versionId, operatorId, "SKILL_VERSION_REVOKE",
                "Skill编码=" + skill.getCode() + "，撤销版本=V" + version.getVersionNo());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, SkillLifecycleStatus status, Long operatorId) {
        SkillEntity skill = requireAccessibleSkill(id, operatorId);
        skill.setStatus(status);
        skill.setUpdatedBy(operatorId);
        skillRepo.save(skill);
        auditService.record(
                "SKILL", "SKILL", id, operatorId, "SKILL_STATUS_CHANGE",
                "Skill编码=" + skill.getCode() + "，状态=" + status.getCode());
    }

    private SkillEntity requireAccessibleSkill(Long id, Long operatorId) {
        SkillEntity skill = skillRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Skill不存在"));
        requireAccessible(skill, operatorId);
        return skill;
    }

    private static void requireAccessible(SkillEntity skill, Long operatorId) {
        if (skill.getVisibility() == SkillVisibility.PRIVATE
                && !Objects.equals(skill.getOwnerUserId(), operatorId)) {
            throw new BusinessException(HttpStatusEnum.FORBIDDEN, "无权访问该Skill");
        }
    }

    private SkillDraftEntity requireDraft(Long skillId) {
        return draftRepo.findBySkillId(skillId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Skill草稿不存在"));
    }

    private SkillVersionEntity requireVersion(Long skillId, Long versionId) {
        return versionRepo.findBySkillIdAndId(skillId, versionId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Skill发布版本不存在"));
    }

    private boolean isVersionReady(SkillEntity skill, SkillVersionEntity version) {
        if (skill.getStatus() != SkillLifecycleStatus.ACTIVE
                || skill.getSecurityStatus() != SkillSecurityStatus.NORMAL
                || Objects.isNull(version)
                || Objects.nonNull(version.getRevokedAt())
                || !Objects.equals(version.getSkillId(), skill.getId())
                || !Objects.equals(version.getSchemaVersion(), SKILL_SCHEMA_VERSION)
                || Objects.isNull(version.getSnapshot())
                || !Objects.equals(version.getSnapshot().getSchemaVersion(), SKILL_SCHEMA_VERSION)
                || !Objects.equals(version.getSnapshot().getCode(), skill.getCode())) {
            return false;
        }
        return Objects.equals(snapshotChecksum.calculate(version.getSnapshot()), version.getSnapshotChecksum());
    }

    private static SkillVersionSnapshot buildSnapshot(SkillEntity skill, SkillDraftEntity draft) {
        return SkillVersionSnapshot.builder()
                .schemaVersion(draft.getSchemaVersion())
                .code(skill.getCode())
                .name(skill.getName())
                .description(skill.getDescription())
                .instructions(draft.getInstructions())
                .activationMode(draft.getActivationMode())
                .requestedToolCodes(List.copyOf(draft.getRequestedToolCodes()))
                .build();
    }

    private static void validateCreatePolicy(CreateSkillRequest request) {
        if (request.getSourceType() == SkillSourceType.THIRD_PARTY_IMPORTED) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "第三方Skill必须通过隔离导入流程创建");
        }
        validateVisibility(request.getSourceType(), request.getVisibility());
    }

    private static void validateVisibility(SkillSourceType sourceType, SkillVisibility visibility) {
        if (sourceType == SkillSourceType.SYSTEM_PRESET && visibility != SkillVisibility.SYSTEM) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "平台预设Skill只能设为平台可见");
        }
        if (sourceType == SkillSourceType.USER_CREATED && visibility != SkillVisibility.PRIVATE) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "用户创建的Skill只能由创建者使用");
        }
    }

    private static String versionName(String requestedName, int versionNo) {
        return CharSequenceUtil.isBlank(requestedName) ? "V" + versionNo : requestedName.trim();
    }

    private static String trimToNull(String value) {
        return CharSequenceUtil.isBlank(value) ? null : value.trim();
    }
}
