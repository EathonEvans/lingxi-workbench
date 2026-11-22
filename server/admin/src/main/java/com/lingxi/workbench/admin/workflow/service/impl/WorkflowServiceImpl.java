package com.lingxi.workbench.admin.workflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.workflow.model.config.WorkflowCanvasState;
import com.lingxi.workbench.admin.workflow.model.converter.WorkflowConverter;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowDraftEntity;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowEntity;
import com.lingxi.workbench.admin.workflow.model.entity.WorkflowVersionEntity;
import com.lingxi.workbench.admin.workflow.model.request.CreateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.PublishWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.RestoreWorkflowDraftRequest;
import com.lingxi.workbench.admin.workflow.model.request.SaveWorkflowDraftRequest;
import com.lingxi.workbench.admin.workflow.model.request.UpdateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.ValidateWorkflowRequest;
import com.lingxi.workbench.admin.workflow.model.request.WorkflowPageQuery;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowDetailVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowValidationIssueVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowValidationResultVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVO;
import com.lingxi.workbench.admin.workflow.model.vo.WorkflowVersionVO;
import com.lingxi.workbench.admin.workflow.repo.WorkflowDraftRepo;
import com.lingxi.workbench.admin.workflow.repo.WorkflowRepo;
import com.lingxi.workbench.admin.workflow.repo.WorkflowVersionRepo;
import com.lingxi.workbench.admin.workflow.service.WorkflowDraftResolver;
import com.lingxi.workbench.admin.workflow.service.WorkflowService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowEdgeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.EndNodeConfig;
import com.lingxi.workbench.workflow.model.config.StartNodeConfig;
import com.lingxi.workbench.workflow.service.WorkflowDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Workflow 管理服务实现。 */
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private static final int SCHEMA_VERSION = WorkflowDefinitionValidator.SUPPORTED_SCHEMA_VERSION;
    private static final String EXISTING_CODE_MSG = "Workflow编码已存在";
    private static final String NOT_EXIST_MSG = "Workflow不存在";
    private static final String DRAFT_NOT_EXIST_MSG = "草稿不存在，请先保存";
    private static final String CONFLICT_MSG = "草稿已被其他操作修改，请刷新后重试";
    private static final String SAME_CHECKSUM_MSG = "当前草稿与已发布版本完全相同，无需重复发布";
    private static final String START_NODE_ID = "start";
    private static final String END_NODE_ID = "end";

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
        draft.setCanvasState(initialCanvasState());
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
        WorkflowEntity workflow = requireWorkflow(id);
        WorkflowDraftEntity draft = requireDraft(id);
        if (!Objects.equals(workflow.getCode(), request.getDefinition().code())) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Workflow定义编码与主记录不一致");
        }
        draft.setDefinition(request.getDefinition());
        draft.setCanvasState(request.getCanvasState());
        draft.setChangeNote(request.getChangeNote());
        draft.setUpdatedBy(userId);
        if (!draftRepo.isUpdatedWithRevision(draft, request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, CONFLICT_MSG);
        }
        return detail(id);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowValidationResultVO validate(Long id, ValidateWorkflowRequest request) {
        WorkflowEntity workflow = requireWorkflow(id);
        WorkflowDefinition definition = request.getDefinition();
        if (!Objects.equals(workflow.getCode(), definition.code())) {
            return new WorkflowValidationResultVO(false, List.of(new WorkflowValidationIssueVO(
                    "WORKFLOW_CODE_MISMATCH", null, "Workflow定义编码与主记录不一致")));
        }
        try {
            draftResolver.resolveForPublish(definition);
            return new WorkflowValidationResultVO(true, List.of());
        } catch (BusinessException exception) {
            String nodeId = definition.nodes().stream()
                    .map(node -> node.id())
                    .filter(exception.getMessage()::contains)
                    .findFirst()
                    .orElse(null);
            return new WorkflowValidationResultVO(false, List.of(
                    new WorkflowValidationIssueVO("WORKFLOW_INVALID", nodeId, exception.getMessage())));
        }
    }

    @Override
    @Transactional
    public WorkflowDetailVO publish(Long id, PublishWorkflowRequest request, Long userId) {
        WorkflowEntity entity = workflowRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, NOT_EXIST_MSG));
        if (entity.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的Workflow不能发布");
        }
        WorkflowDraftEntity draft = draftRepo.findByWorkflowIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, DRAFT_NOT_EXIST_MSG));
        if (!Objects.equals(draft.getRevision(), request.getExpectedDraftRevision())) {
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
        version.setCanvasSnapshot(draft.getCanvasState());
        version.setSnapshotChecksum(checksum);
        String changeNote = Optional.ofNullable(request.getChangeNote())
                .filter(note -> !note.isBlank())
                .map(String::trim)
                .orElse(draft.getChangeNote());
        version.setChangeNote(changeNote);
        version.setPublishedBy(userId);
        version.setPublishedAt(OffsetDateTime.now());
        versionRepo.save(version);

        entity.setPublishedVersionId(version.getId());
        entity.setUpdatedBy(userId);
        workflowRepo.save(entity);
        draftRepo.updatePublicationBase(draft.getId(), version.getId(), userId, request.getExpectedDraftRevision());

        return detail(id);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowVersionVO versionDetail(Long id, Long versionId) {
        requireWorkflow(id);
        return converter.toVersionVO(requireVersion(id, versionId));
    }

    @Override
    @Transactional
    public void activateVersion(Long id, Long versionId, Long userId) {
        WorkflowEntity workflow = workflowRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, NOT_EXIST_MSG));
        if (workflow.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的Workflow不能切换版本");
        }
        requireVersion(id, versionId);
        workflow.setPublishedVersionId(versionId);
        workflow.setUpdatedBy(userId);
        workflowRepo.save(workflow);
    }

    @Override
    @Transactional
    public WorkflowDetailVO restoreDraft(Long id, Long versionId, RestoreWorkflowDraftRequest request, Long userId) {
        requireWorkflow(id);
        WorkflowVersionEntity version = requireVersion(id, versionId);
        WorkflowDraftEntity draft = requireDraft(id);
        draft.setBaseVersionId(versionId);
        draft.setDefinition(version.getDefinitionSnapshot());
        draft.setCanvasState(version.getCanvasSnapshot());
        draft.setChangeNote("基于" + version.getVersionName() + "恢复草稿");
        draft.setUpdatedBy(userId);
        if (!draftRepo.isUpdatedWithRevision(draft, request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, CONFLICT_MSG);
        }
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
        WorkflowNodeDefinition start = new WorkflowNodeDefinition(START_NODE_ID, "开始", WorkflowNodeType.START, new StartNodeConfig());
        WorkflowNodeDefinition end = new WorkflowNodeDefinition(END_NODE_ID, "结束", WorkflowNodeType.END, new EndNodeConfig(Map.of("response", "query")));
        List<WorkflowNodeDefinition> nodes = List.of(start, end);
        List<WorkflowEdgeDefinition> edges = List.of(new WorkflowEdgeDefinition(START_NODE_ID, END_NODE_ID, null));
        return new WorkflowDefinition(SCHEMA_VERSION, code, nodes, edges);
    }

    private WorkflowCanvasState initialCanvasState() {
        return new WorkflowCanvasState(
                Map.of(
                        START_NODE_ID, new WorkflowCanvasState.NodePosition(80, 180),
                        END_NODE_ID, new WorkflowCanvasState.NodePosition(520, 180)),
                new WorkflowCanvasState.Viewport(0, 0, 1));
    }

    private WorkflowEntity requireWorkflow(Long id) {
        return workflowRepo.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, NOT_EXIST_MSG));
    }

    private WorkflowDraftEntity requireDraft(Long workflowId) {
        return draftRepo.findByWorkflowId(workflowId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, DRAFT_NOT_EXIST_MSG));
    }

    private WorkflowVersionEntity requireVersion(Long workflowId, Long versionId) {
        return versionRepo.findByWorkflowIdAndId(workflowId, versionId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Workflow发布版本不存在"));
    }
}
