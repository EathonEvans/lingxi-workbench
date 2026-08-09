package com.lingxi.workbench.admin.application.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.application.model.converter.ApplicationConverter;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationDraftEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationEntity;
import com.lingxi.workbench.admin.application.model.entity.AiApplicationVersionEntity;
import com.lingxi.workbench.admin.application.model.request.ApplicationPageQuery;
import com.lingxi.workbench.admin.application.model.request.CreateApplicationRequest;
import com.lingxi.workbench.admin.application.model.request.PublishApplicationRequest;
import com.lingxi.workbench.admin.application.model.request.SaveApplicationDraftRequest;
import com.lingxi.workbench.admin.application.model.request.UpdateApplicationRequest;
import com.lingxi.workbench.admin.application.model.vo.ApplicationDetailVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationIssueVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationValidationResultVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVersionDetailVO;
import com.lingxi.workbench.admin.application.model.vo.ApplicationVersionVO;
import com.lingxi.workbench.admin.application.repo.AiApplicationDraftRepo;
import com.lingxi.workbench.admin.application.repo.AiApplicationRepo;
import com.lingxi.workbench.admin.application.repo.AiApplicationVersionRepo;
import com.lingxi.workbench.admin.application.service.ApplicationDefinitionResolution;
import com.lingxi.workbench.admin.application.service.ApplicationDraftValidator;
import com.lingxi.workbench.admin.application.service.ApplicationService;
import com.lingxi.workbench.admin.application.service.ApplicationSnapshotChecksum;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

/**
 * Application 控制面业务实现。
 */
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {
    private final AiApplicationRepo applicationRepo;
    private final AiApplicationDraftRepo draftRepo;
    private final AiApplicationVersionRepo versionRepo;
    private final ApplicationDraftValidator draftValidator;
    private final ApplicationSnapshotChecksum snapshotChecksum;
    private final ApplicationConverter converter;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    /** 分页查询 Application，同时填充草稿 revision 和生效版本号到列表 VO。 */
    public PageResult<ApplicationVO> page(ApplicationPageQuery query) {
        PageResult<AiApplicationEntity> page = applicationRepo.findPage(query);
        List<Long> ids = page.records().stream().map(AiApplicationEntity::getId).toList();
        Map<Long, AiApplicationDraftEntity> drafts =
                draftRepo.findAllByApplicationIds(ids).stream()
                        .collect(Collectors.toMap(
                                AiApplicationDraftEntity::getApplicationId,
                                Function.identity()));
        List<Long> publishedVersionIds = page.records().stream()
                .map(AiApplicationEntity::getPublishedVersionId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, AiApplicationVersionEntity> versions =
                versionRepo.findAllByIds(publishedVersionIds).stream()
                        .collect(Collectors.toMap(
                                AiApplicationVersionEntity::getId,
                                Function.identity(),
                                (left, right) -> left));
        return page.map(application -> converter.toListVO(
                application,
                versions.get(application.getPublishedVersionId()),
                drafts.get(application.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    /** 查看 Application 编辑详情，组装主记录、草稿和生效版本。 */
    public ApplicationDetailVO detail(Long id) {
        AiApplicationEntity application = requireApplication(id);
        AiApplicationDraftEntity draft = requireDraft(id);
        AiApplicationVersionEntity publishedVersion =
                application.getPublishedVersionId() == null
                        ? null
                        : versionRepo.findById(application.getPublishedVersionId()).orElse(null);
        return converter.toDetailVO(application, publishedVersion, draft);
    }

    @Override
    @Transactional(readOnly = true)
    /** 查看 Application 历史发布版本列表。 */
    public List<ApplicationVersionVO> versions(Long id) {
        requireApplication(id);
        return versionRepo.findAll(id).stream().map(converter::toVersionVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    /** 查看指定发布版本详情，含完整 Workflow 定义快照。 */
    public ApplicationVersionDetailVO versionDetail(Long id, Long versionId) {
        requireApplication(id);
        return converter.toVersionDetailVO(requireVersion(id, versionId));
    }

    @Override
    @Transactional
    /** 创建 Application，同时初始化空行为草稿，记录审计。 */
    public ApplicationDetailVO create(
            CreateApplicationRequest request, Long operatorId) {
        String code = request.getCode().trim().toLowerCase();
        if (applicationRepo.existsByCode(code, null)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "应用编码已存在");
        }

        AiApplicationEntity application = new AiApplicationEntity();
        application.setName(request.getName().trim());
        application.setCode(code);
        application.setDescription(trimToNull(request.getDescription()));
        application.setStatus(EnableStatus.ACTIVE);
        application.setCreatedBy(operatorId);
        application.setUpdatedBy(operatorId);
        applicationRepo.save(application);

        AiApplicationDraftEntity draft = new AiApplicationDraftEntity();
        draft.setApplicationId(application.getId());
        draft.setSchemaVersion(APPLICATION_SCHEMA_VERSION);
        draft.setDefinition(defaultDefinition());
        draft.setRevision(0L);
        draft.setCreatedBy(operatorId);
        draft.setUpdatedBy(operatorId);
        draftRepo.insert(draft);

        auditService.record(
                "APPLICATION",
                "APPLICATION",
                application.getId(),
                operatorId,
                "APPLICATION_CREATE",
                "应用编码=" + code);
        return detail(application.getId());
    }

    @Override
    @Transactional
    /** 修改 Application 名称和描述元信息。 */
    public ApplicationDetailVO update(
            Long id, UpdateApplicationRequest request, Long operatorId) {
        AiApplicationEntity application = requireApplication(id);
        application.setName(request.getName().trim());
        application.setDescription(trimToNull(request.getDescription()));
        application.setUpdatedBy(operatorId);
        applicationRepo.save(application);
        auditService.record(
                "APPLICATION",
                "APPLICATION",
                id,
                operatorId,
                "APPLICATION_UPDATE",
                "应用编码=" + application.getCode());
        return detail(id);
    }

    @Override
    @Transactional
    /** 保存 Application 草稿，并以 revision 乐观锁保护并发编辑。 */
    public ApplicationDetailVO saveDraft(
            Long id, SaveApplicationDraftRequest request, Long operatorId) {
        requireApplication(id);
        AiApplicationDraftEntity draft = draftRepo.findByApplicationIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "Application草稿不存在"));
        if (!Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "Application草稿已被其他操作修改，请刷新后重试");
        }
        draft.setSchemaVersion(APPLICATION_SCHEMA_VERSION);
        draft.setDefinition(ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(request.getCollaboration())
                .optionalFastRoutes(request.getOptionalFastRoutes())
                .memoryConf(Objects.requireNonNullElseGet(request.getMemory(), com.lingxi.workbench.workflow.model.MemoryConfig::disabled))
                .contextPolicy(Objects.requireNonNullElseGet(request.getContextPolicy(), com.lingxi.workbench.workflow.model.ApplicationContextPolicy::defaults))
                .defaultAgent(request.getDefaultAgent())
                .plannerPolicy(Objects.requireNonNullElseGet(request.getPlannerPolicy(), com.lingxi.workbench.workflow.model.PlannerPolicy::disabled))
                .capabilityCatalog(Objects.requireNonNullElseGet(request.getCapabilityCatalog(), com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot::empty))
                .runtimePolicy(Objects.requireNonNullElseGet(request.getRuntimePolicy(), com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy::defaults))
                .build());
        draft.setChangeNote(trimToNull(request.getChangeNote()));
        draft.setUpdatedBy(operatorId);
        if (!draftRepo.updateWithRevision(draft, request.getExpectedRevision())) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "Application草稿已被其他操作修改，请刷新后重试");
        }
        auditService.record(
                "APPLICATION",
                "APPLICATION_DRAFT",
                draft.getId(),
                operatorId,
                "APPLICATION_DRAFT_SAVE",
                "Application主键=" + id
                        + "，revision=" + (request.getExpectedRevision() + 1));
        return detail(id);
    }

    /**
     * 对当前草稿的 Workflow 执行发布前校验：结构合法性和 Agent 节点引用解析。
     */
    @Override
    @Transactional(readOnly = true)
    public ApplicationValidationResultVO validate(Long id) {
        requireApplication(id);
        ApplicationDefinitionResolution resolution =
                draftValidator.resolveForPublish(requireDraft(id).getDefinition());
        return new ApplicationValidationResultVO(
                resolution.valid(), resolution.issues());
    }

    /**
     * 校验草稿、锁定 Agent 版本引用、生成 SHA-256 快照，去重后创建不可变版本。
     */
    @Override
    @Transactional
    public ApplicationVersionDetailVO publish(
            Long id, PublishApplicationRequest request, Long operatorId) {
        AiApplicationEntity application = applicationRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "Application不存在"));
        AiApplicationDraftEntity draft = draftRepo.findByApplicationIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "Application草稿不存在"));
        if (!Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "Application草稿已变化，请重新校验后发布");
        }

        ApplicationDefinitionResolution resolution =
                draftValidator.resolveForPublish(draft.getDefinition());
        if (!resolution.valid()) {
            String message = resolution.issues().stream()
                    .map(ApplicationValidationIssueVO::message)
                    .collect(Collectors.joining("；"));
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST,
                    "Application发布校验失败：" + message);
        }

        ApplicationDefinition snapshot = resolution.definition();
        String checksum = snapshotChecksum.calculate(snapshot);
        if (versionRepo.existsByChecksum(id, checksum)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "当前草稿与已发布版本完全相同，无需重复发布");
        }
        int versionNo = versionRepo.nextVersionNo(id);
        AiApplicationVersionEntity version = new AiApplicationVersionEntity();
        version.setApplicationId(id);
        version.setVersionNo(versionNo);
        version.setVersionName(versionName(request.getVersionName(), versionNo));
        version.setSchemaVersion(snapshot.schemaVersion());
        version.setDefinitionSnapshot(snapshot);
        version.setSnapshotChecksum(checksum);
        version.setChangeNote(draft.getChangeNote());
        version.setPublishedBy(operatorId);
        version.setPublishedAt(OffsetDateTime.now());
        versionRepo.insert(version);

        application.setPublishedVersionId(version.getId());
        application.setUpdatedBy(operatorId);
        applicationRepo.save(application);
        draftRepo.updatePublicationBase(
                draft.getId(), version.getId(), operatorId, request.getExpectedRevision());
        auditService.record(
                "APPLICATION",
                "APPLICATION",
                id,
                operatorId,
                "APPLICATION_PUBLISH",
                "应用编码=" + application.getCode()
                        + "，发布版本=V" + versionNo
                        + "，checksum=" + checksum);
        return converter.toVersionDetailVO(version);
    }

    @Override
    @Transactional
    /** 将 Application 当前生效版本切换为指定历史版本。 */
    public void activateVersion(Long id, Long versionId, Long operatorId) {
        AiApplicationEntity application = applicationRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "Application不存在"));
        if (application.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "停用的Application不能切换版本");
        }
        AiApplicationVersionEntity version = requireVersion(id, versionId);
        application.setPublishedVersionId(versionId);
        application.setUpdatedBy(operatorId);
        applicationRepo.save(application);
        auditService.record(
                "APPLICATION",
                "APPLICATION",
                id,
                operatorId,
                "APPLICATION_VERSION_ACTIVATE",
                "应用编码=" + application.getCode()
                        + "，生效版本=V" + version.getVersionNo());
    }

    @Override
    @Transactional
    /** 启用或停用 Application，记录操作审计。 */
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        AiApplicationEntity application = requireApplication(id);
        application.setStatus(status);
        application.setUpdatedBy(operatorId);
        applicationRepo.save(application);
        auditService.record(
                "APPLICATION",
                "APPLICATION",
                id,
                operatorId,
                "APPLICATION_STATUS_CHANGE",
                "应用编码=" + application.getCode() + "，状态=" + status.getCode());
    }

    private AiApplicationEntity requireApplication(Long id) {
        return applicationRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "Application不存在"));
    }

    private AiApplicationDraftEntity requireDraft(Long applicationId) {
        return draftRepo.findByApplicationId(applicationId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "Application草稿不存在"));
    }

    private AiApplicationVersionEntity requireVersion(
            Long applicationId, Long versionId) {
        return versionRepo.findByApplicationIdAndId(applicationId, versionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "Application发布版本不存在"));
    }

    private static ApplicationDefinition defaultDefinition() {
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .memoryConf(com.lingxi.workbench.workflow.model.MemoryConfig.disabled())
                .contextPolicy(com.lingxi.workbench.workflow.model.ApplicationContextPolicy.defaults())
                .plannerPolicy(com.lingxi.workbench.workflow.model.PlannerPolicy.disabled())
                .capabilityCatalog(com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot.empty())
                .runtimePolicy(com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy.defaults())
                .build();
    }

    private static String versionName(String requestedName, int versionNo) {
        return CharSequenceUtil.isBlank(requestedName)
                ? "V" + versionNo
                : requestedName.trim();
    }

    private static String trimToNull(String value) {
        return CharSequenceUtil.isBlank(value) ? null : value.trim();
    }
}
