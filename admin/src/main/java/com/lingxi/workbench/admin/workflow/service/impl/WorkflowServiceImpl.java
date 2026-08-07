package com.lingxi.workbench.admin.workflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.workflow.model.converter.WorkflowConverter;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowDraftEntity;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowEntity;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowVersionEntity;
import com.lingxi.workbench.admin.workflow.model.request.CreateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.PublishWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.SaveWorkflowDraftRequest;
import com.lingxi.workbench.admin.workflow.model.request.UpdateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.WorkflowPageQuery;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowDetailVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVersionVO;
import com.lingxi.workbench.admin.workflow.repo.WorkflowDraftRepo;
import com.lingxi.workbench.admin.workflow.repo.WorkflowRepo;
import com.lingxi.workbench.admin.workflow.repo.WorkflowVersionRepo;
import com.lingxi.workbench.admin.workflow.service.WorkflowService;
import com.lingxi.workbench.admin.workflow.service.WorkflowDraftResolver;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Workflow 管理服务实现。 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private static final int SCHEMA_VERSION = 1;
    private static final String EXISTING_CODE_MSG = "Workflow编码已存在";
    private static final String NOT_EXIST_MSG = "Workflow不存在";
    private static final String DRAFT_NOT_EXIST_MSG = "草稿不存在，请先保存";
    private static final String CONFLICT_MSG = "草稿已被其他操作修改，请刷新后重试";
    private static final String SAME_CHECKSUM_MSG = "当前草稿与已发布版本完全相同，无需重复发布";

    private final WorkflowRepo workflowRepo;
    private final WorkflowDraftRepo draftRepo;
    private final WorkflowVersionRepo versionRepo;
    private final WorkflowConverter converter;
    private final WorkflowDraftResolver draftResolver;

    // 规范 JSON 序列化器：按字段排序以保证 checksum 稳定
    private final JsonMapper canonicalMapper = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Override
    @Transactional(readOnly = true)
    public PageResult<WorkflowVO> page(WorkflowPageQuery query) {
        return workflowRepo.findPage(query).map(converter::toListVO);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowDetailVO detail(Long id) {
        WorkflowEntity entity = requireWorkflow(id);
        WorkflowDraftEntity draft = draftRepo.findByWorkflowId(id).orElse(null);
        List<WorkflowVersionVO> versions = versionRepo.findByWorkflowId(id).stream()
                .map(converter::toVersionVO)
                .toList();
        return converter.toDetailVO(entity, draft, versions);
    }

    @Override
    @Transactional
    public WorkflowDetailVO create(CreateWorkflowRequest request, Long userId) {
        if (workflowRepo.existsByCode(request.getCode(), null)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, EXISTING_CODE_MSG);
        }
        WorkflowEntity entity = new WorkflowEntity();
        entity.setName(request.getName());
        entity.setCode(request.getCode().toLowerCase().trim());
        entity.setDescription(request.getDescription());
        entity.setStatus(EnableStatus.ACTIVE);
        entity.setCreatedBy(userId);
        entity.setUpdatedBy(userId);
        workflowRepo.save(entity);

        WorkflowDraftEntity draft = new WorkflowDraftEntity();
        draft.setWorkflowId(entity.getId());
        draft.setSchemaVersion(SCHEMA_VERSION);
        // 初始空 Workflow：仅含 START → END
        draft.setDefinition(emptyDraft(request.getCode()));
        draft.setRevision(0L);
        draft.setCreatedBy(userId);
        draft.setUpdatedBy(userId);
        draftRepo.save(draft);

        return detail(entity.getId());
    }

    @Override
    @Transactional
    public WorkflowDetailVO update(Long id, UpdateWorkflowRequest request, Long userId) {
        WorkflowEntity entity = requireWorkflow(id);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setUpdatedBy(userId);
        workflowRepo.save(entity);
        return detail(id);
    }

    @Override
    @Transactional
    public WorkflowDetailVO saveDraft(Long id, SaveWorkflowDraftRequest request, Long userId) {
        requireWorkflow(id);
        WorkflowDraftEntity draft = requireDraft(id);
        if (Objects.nonNull(request.getExpectedRevision()) && !Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, CONFLICT_MSG);
        }
        draft.setDefinition(request.getDefinition());
        draft.setChangeNote(request.getChangeNote());
        draft.setRevision(draft.getRevision() + 1);
        draft.setUpdatedBy(userId);
        draftRepo.save(draft);
        return detail(id);
    }

    @Override
    @Transactional
    public WorkflowDetailVO publish(Long id, PublishWorkflowRequest request, Long userId) {
        WorkflowEntity entity = workflowRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, NOT_EXIST_MSG));
        WorkflowDraftEntity draft = requireDraft(id);
        if (Objects.nonNull(request.getExpectedDraftRevision()) && !Objects.equals(draft.getRevision(), request.getExpectedDraftRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, CONFLICT_MSG);
        }

        WorkflowDefinition snapshot = draftResolver.resolveForPublish(draft.getDefinition());
        String checksum = calculateChecksum(snapshot);
        // 去重：相同内容不重复发布
        if (existsSameChecksum(id, checksum)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, SAME_CHECKSUM_MSG);
        }

        int versionNo = versionRepo.nextVersionNo(id);
        WorkflowVersionEntity version = new WorkflowVersionEntity();
        version.setWorkflowId(id);
        version.setVersionNo(versionNo);
        version.setVersionName(Objects.toString(request.getVersionName(), "V" + versionNo));
        version.setSchemaVersion(SCHEMA_VERSION);
        version.setDefinitionSnapshot(snapshot);
        version.setSnapshotChecksum(checksum);
        version.setChangeNote(draft.getChangeNote());
        version.setPublishedBy(userId);
        version.setPublishedAt(OffsetDateTime.now());
        versionRepo.save(version);

        entity.setPublishedVersionId(version.getId());
        entity.setUpdatedBy(userId);
        workflowRepo.save(entity);

        return detail(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowReference> findReference(Long id) {
        return workflowRepo.findById(id).map(workflow -> new WorkflowReference(
                workflow.getId(),
                workflow.getStatus() == EnableStatus.ACTIVE,
                workflow.getPublishedVersionId(),
                workflow.getName()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> findVersion(Long workflowId, Long versionId) {
        return versionRepo.findByWorkflowIdAndId(workflowId, versionId)
                .map(WorkflowVersionEntity::getDefinitionSnapshot);
    }

    /** 计算 WorkflowDefinition 的 SHA-256 checksum。 */
    private String calculateChecksum(WorkflowDefinition definition) {
        try {
            byte[] canonicalJson = canonicalMapper.writeValueAsBytes(definition);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalJson));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "Workflow快照计算失败");
        }
    }

    /** 校验指定 Workflow 下是否已存在相同 checksum 的版本。 */
    private boolean existsSameChecksum(Long workflowId, String checksum) {
        return versionRepo.findByWorkflowId(workflowId).stream()
                .anyMatch(v -> Objects.equals(v.getSnapshotChecksum(), checksum));
    }

    /** 初始化空 Workflow 草稿：仅含 START → END。 */
    private WorkflowDefinition emptyDraft(String code) {
        com.lingxi.workbench.workflow.model.WorkflowNodeDefinition start =
                new com.lingxi.workbench.workflow.model.WorkflowNodeDefinition("__start__", "开始", com.lingxi.workbench.workflow.model.WorkflowNodeType.START, new com.lingxi.workbench.workflow.model.config.StartNodeConfig());
        com.lingxi.workbench.workflow.model.WorkflowNodeDefinition end =
                new com.lingxi.workbench.workflow.model.WorkflowNodeDefinition("__end__", "结束", com.lingxi.workbench.workflow.model.WorkflowNodeType.END, new com.lingxi.workbench.workflow.model.config.EndNodeConfig(java.util.Map.of("response", "query")));
        List<com.lingxi.workbench.workflow.model.WorkflowNodeDefinition> nodes = List.of(start, end);
        List<com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition> edges = List.of(
                new com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition("__start__", "__end__", null));
        return new WorkflowDefinition(SCHEMA_VERSION, code, nodes, edges);
    }

    private WorkflowEntity requireWorkflow(Long id) {
        return workflowRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, NOT_EXIST_MSG));
    }

    private WorkflowDraftEntity requireDraft(Long workflowId) {
        return draftRepo.findByWorkflowId(workflowId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, DRAFT_NOT_EXIST_MSG));
    }
}
