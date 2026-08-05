package com.lingxi.workbench.admin.intent.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.intent.model.converter.IntentTreeConverter;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeDraftEntity;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeEntity;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeVersionEntity;
import com.lingxi.workbench.admin.intent.model.request.CreateIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.request.IntentTreePageQuery;
import com.lingxi.workbench.admin.intent.model.request.PublishIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.request.SaveIntentTreeDraftRequest;
import com.lingxi.workbench.admin.intent.model.request.UpdateIntentTreeRequest;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeDetailVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeValidationIssueVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeValidationResultVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionDetailVO;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeVersionVO;
import com.lingxi.workbench.admin.intent.repo.IntentTreeDraftRepo;
import com.lingxi.workbench.admin.intent.repo.IntentTreeRepo;
import com.lingxi.workbench.admin.intent.repo.IntentTreeVersionRepo;
import com.lingxi.workbench.admin.intent.repo.IntentTreeVectorRepo;
import com.lingxi.workbench.admin.intent.service.IntentTreeDefinitionValidator;
import com.lingxi.workbench.admin.intent.service.IntentTreeService;
import com.lingxi.workbench.admin.intent.service.IntentTreeSnapshotChecksum;
import com.lingxi.workbench.admin.intent.service.IntentVectorSourceBuilder;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeEmbeddingModelResolver;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.bo.ModelInstanceReferenceBO;
import com.lingxi.workbench.admin.model.service.ModelInstanceService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 意图树控制面业务实现。 */
@Service
@RequiredArgsConstructor
public class IntentTreeServiceImpl implements IntentTreeService {

    private static final int SCHEMA_VERSION = 1;

    private final IntentTreeRepo intentTreeRepo;
    private final IntentTreeDraftRepo draftRepo;
    private final IntentTreeVersionRepo versionRepo;
    private final IntentTreeDefinitionValidator definitionValidator;
    private final IntentTreeSnapshotChecksum snapshotChecksum;
    private final IntentTreeConverter converter;
    private final ModelInstanceService modelInstanceService;
    private final KnowledgeEmbeddingModelResolver embeddingModelResolver;
    private final EmbeddingGateway embeddingGateway;
    private final IntentVectorSourceBuilder vectorSourceBuilder;
    private final IntentTreeVectorRepo vectorRepo;
    private final PlatformTransactionManager transactionManager;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<IntentTreeVO> page(IntentTreePageQuery query) {
        PageResult<IntentTreeEntity> page = intentTreeRepo.findPage(query);
        List<Long> treeIds = page.records().stream().map(IntentTreeEntity::getId).toList();
        Map<Long, IntentTreeDraftEntity> drafts = draftRepo.findAllByIntentTreeIds(treeIds).stream().collect(Collectors.toMap(IntentTreeDraftEntity::getIntentTreeId, Function.identity()));
        Map<Long, IntentTreeVersionEntity> versions = loadPublishedVersions(page.records());
        return page.map(tree -> converter.toListVO(tree, versions.get(tree.getPublishedVersionId()), drafts.get(tree.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public IntentTreeDetailVO detail(Long id) {
        IntentTreeEntity tree = requireTree(id);
        IntentTreeDraftEntity draft = requireDraft(id);
        IntentTreeVersionEntity publishedVersion = Objects.isNull(tree.getPublishedVersionId())
                ? null
                : requireVersion(id, tree.getPublishedVersionId());
        return converter.toDetailVO(tree, publishedVersion, draft);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntentTreeVersionVO> versions(Long id) {
        requireTree(id);
        return versionRepo.findAll(id).stream().map(converter::toVersionVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IntentTreeVersionDetailVO versionDetail(Long id, Long versionId) {
        requireTree(id);
        return converter.toVersionDetailVO(requireVersion(id, versionId));
    }

    @Override
    @Transactional
    public IntentTreeDetailVO create(CreateIntentTreeRequest request, Long operatorId) {
        String code = request.getCode().trim().toLowerCase();
        if (intentTreeRepo.existsByCode(code, null)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树编码已存在");
        }
        IntentTreeEntity tree = new IntentTreeEntity();
        tree.setName(request.getName().trim());
        tree.setCode(code);
        tree.setDescription(trimToNull(request.getDescription()));
        tree.setStatus(EnableStatus.ACTIVE);
        tree.setCreatedBy(operatorId);
        tree.setUpdatedBy(operatorId);
        intentTreeRepo.save(tree);

        IntentTreeDefinition definition = defaultDefinition();
        IntentTreeDraftEntity draft = new IntentTreeDraftEntity();
        draft.setIntentTreeId(tree.getId());
        draft.setSchemaVersion(SCHEMA_VERSION);
        draft.setEmbeddingModelInstanceId(definition.embeddingModelInstanceId());
        draft.setDefinition(definition);
        draft.setRevision(0L);
        draft.setCreatedBy(operatorId);
        draft.setUpdatedBy(operatorId);
        draftRepo.insert(draft);

        auditService.record("INTENT", "INTENT_TREE", tree.getId(), operatorId, "INTENT_TREE_CREATE", "意图树编码=" + code);
        return detail(tree.getId());
    }

    @Override
    @Transactional
    public IntentTreeDetailVO update(Long id, UpdateIntentTreeRequest request, Long operatorId) {
        IntentTreeEntity tree = requireTree(id);
        tree.setName(request.getName().trim());
        tree.setDescription(trimToNull(request.getDescription()));
        tree.setUpdatedBy(operatorId);
        intentTreeRepo.save(tree);
        auditService.record("INTENT", "INTENT_TREE", id, operatorId, "INTENT_TREE_UPDATE", "意图树编码=" + tree.getCode());
        return detail(id);
    }

    @Override
    @Transactional
    public IntentTreeDetailVO saveDraft(Long id, SaveIntentTreeDraftRequest request, Long operatorId) {
        requireTree(id);
        IntentTreeDraftEntity draft = draftRepo.findByIntentTreeIdForUpdate(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "意图树草稿不存在"));
        if (!Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树草稿已被其他操作修改，请刷新后重试");
        }
        IntentTreeDefinition definition = request.getDefinition();
        if (definition.schemaVersion() != SCHEMA_VERSION) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "当前仅支持意图树Schema V1");
        }
        if (Objects.nonNull(definition.nodes()) && definition.nodes().size() > 100) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "单棵意图树最多允许100个节点");
        }
        draft.setSchemaVersion(SCHEMA_VERSION);
        draft.setEmbeddingModelInstanceId(definition.embeddingModelInstanceId());
        draft.setDefinition(definition);
        draft.setChangeNote(trimToNull(request.getChangeNote()));
        draft.setUpdatedBy(operatorId);
        if (!draftRepo.updateWithRevision(draft, request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树草稿已被其他操作修改，请刷新后重试");
        }
        auditService.record("INTENT", "INTENT_TREE_DRAFT", draft.getId(), operatorId, "INTENT_TREE_DRAFT_SAVE", "意图树主键=" + id + "，revision=" + (request.getExpectedRevision() + 1));
        return detail(id);
    }

    @Override
    @Transactional(readOnly = true)
    public IntentTreeValidationResultVO validate(Long id) {
        requireTree(id);
        List<IntentTreeValidationIssueVO> issues = validateForPublish(requireDraft(id).getDefinition());
        return new IntentTreeValidationResultVO(issues.isEmpty(), issues);
    }

    @Override
    public IntentTreeVersionDetailVO publish(Long id, PublishIntentTreeRequest request, Long operatorId) {
        IntentTreeEntity currentTree = requireTree(id);
        IntentTreeDraftEntity currentDraft = requireDraft(id);
        if (currentTree.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的意图树不能发布");
        }
        if (!Objects.equals(currentDraft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树草稿已变化，请重新校验后发布");
        }
        List<IntentTreeValidationIssueVO> currentIssues = validateForPublish(currentDraft.getDefinition());
        if (!currentIssues.isEmpty()) {
            String message = currentIssues.stream().map(IntentTreeValidationIssueVO::message).collect(Collectors.joining("；"));
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "意图树发布校验失败：" + message);
        }

        String preparedChecksum = snapshotChecksum.calculate(currentDraft.getDefinition());
        if (versionRepo.existsByChecksum(id, preparedChecksum)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前草稿与已发布版本完全相同，无需重复发布");
        }
        List<IntentVectorSourceBuilder.IntentVectorSource> sources = vectorSourceBuilder.build(currentDraft.getDefinition());
        EmbeddingModelRuntimeConfig embeddingConfig = embeddingModelResolver.resolve(currentDraft.getDefinition().embeddingModelInstanceId());
        List<float[]> vectors = embeddingGateway.embed(
                sources.stream().map(IntentVectorSourceBuilder.IntentVectorSource::text).toList(),
                embeddingConfig);
        validateVectors(sources, vectors);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        IntentTreeVersionEntity version = transaction.execute(status -> publishPrepared(
                id,
                request,
                operatorId,
                preparedChecksum,
                sources,
                vectors));
        if (Objects.isNull(version)) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "意图树发布事务未返回版本");
        }
        return converter.toVersionDetailVO(version);
    }

    private IntentTreeVersionEntity publishPrepared(
            Long id,
            PublishIntentTreeRequest request,
            Long operatorId,
            String preparedChecksum,
            List<IntentVectorSourceBuilder.IntentVectorSource> sources,
            List<float[]> vectors) {
        IntentTreeEntity tree = intentTreeRepo.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "意图树不存在"));
        if (tree.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的意图树不能发布");
        }
        IntentTreeDraftEntity draft = draftRepo.findByIntentTreeIdForUpdate(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "意图树草稿不存在"));
        if (!Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树草稿已变化，请重新校验后发布");
        }
        List<IntentTreeValidationIssueVO> issues = validateForPublish(draft.getDefinition());
        if (!issues.isEmpty()) {
            String message = issues.stream().map(IntentTreeValidationIssueVO::message).collect(Collectors.joining("；"));
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "意图树发布校验失败：" + message);
        }

        String checksum = snapshotChecksum.calculate(draft.getDefinition());
        if (!Objects.equals(preparedChecksum, checksum)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树草稿在向量构建期间已变化，请重新发布");
        }
        if (versionRepo.existsByChecksum(id, checksum)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前草稿与已发布版本完全相同，无需重复发布");
        }
        int versionNo = versionRepo.nextVersionNo(id);
        IntentTreeVersionEntity version = new IntentTreeVersionEntity();
        version.setIntentTreeId(id);
        version.setVersionNo(versionNo);
        version.setVersionName(versionName(request.getVersionName(), versionNo));
        version.setSchemaVersion(draft.getDefinition().schemaVersion());
        version.setEmbeddingModelInstanceId(draft.getDefinition().embeddingModelInstanceId());
        version.setDefinitionSnapshot(draft.getDefinition());
        version.setSnapshotChecksum(checksum);
        version.setChangeNote(draft.getChangeNote());
        version.setPublishedBy(operatorId);
        version.setPublishedAt(OffsetDateTime.now());
        versionRepo.insert(version);

        List<IntentTreeVectorRepo.IntentVectorRow> rows = new java.util.ArrayList<>(sources.size());
        for (int index = 0; index < sources.size(); index++) {
            IntentVectorSourceBuilder.IntentVectorSource source = sources.get(index);
            rows.add(IntentTreeVectorRepo.IntentVectorRow.builder()
                    .intentTreeId(id)
                    .intentTreeVersionId(version.getId())
                    .intentNodeId(source.intentNodeId())
                    .intentCode(source.intentCode())
                    .vectorKind(source.vectorKind())
                    .sourceOrdinal(source.sourceOrdinal())
                    .sourceHash(source.sourceHash())
                    .embeddingModelInstanceId(version.getEmbeddingModelInstanceId())
                    .vector(vectors.get(index))
                    .build());
        }
        vectorRepo.insertBatch(rows);

        tree.setPublishedVersionId(version.getId());
        tree.setUpdatedBy(operatorId);
        intentTreeRepo.save(tree);
        draftRepo.updatePublicationBase(draft.getId(), version.getId(), operatorId, request.getExpectedRevision());
        auditService.record("INTENT", "INTENT_TREE", id, operatorId, "INTENT_TREE_PUBLISH", "意图树编码=" + tree.getCode() + "，发布版本=V" + versionNo + "，checksum=" + checksum);
        return version;
    }

    private static void validateVectors(
            List<IntentVectorSourceBuilder.IntentVectorSource> sources,
            List<float[]> vectors) {
        if (vectors == null || vectors.size() != sources.size()) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "Embedding返回数量与意图语义来源不一致");
        }
        if (vectors.isEmpty() || vectors.getFirst() == null) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "Embedding未返回可用向量");
        }
        int dimension = vectors.getFirst().length;
        if (dimension <= 0 || vectors.stream().anyMatch(vector -> vector == null || vector.length != dimension)) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "Embedding返回了空向量或不一致维度");
        }
    }

    @Override
    @Transactional
    public void activateVersion(Long id, Long versionId, Long operatorId) {
        IntentTreeEntity tree = intentTreeRepo.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "意图树不存在"));
        if (tree.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的意图树不能切换版本");
        }
        IntentTreeVersionEntity version = requireVersion(id, versionId);
        tree.setPublishedVersionId(versionId);
        tree.setUpdatedBy(operatorId);
        intentTreeRepo.save(tree);
        auditService.record("INTENT", "INTENT_TREE", id, operatorId, "INTENT_TREE_VERSION_ACTIVATE", "意图树编码=" + tree.getCode() + "，生效版本=V" + version.getVersionNo());
    }

    @Override
    @Transactional
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        IntentTreeEntity tree = requireTree(id);
        tree.setStatus(status);
        tree.setUpdatedBy(operatorId);
        intentTreeRepo.save(tree);
        auditService.record("INTENT", "INTENT_TREE", id, operatorId, "INTENT_TREE_STATUS_CHANGE", "意图树编码=" + tree.getCode() + "，状态=" + status.getCode());
    }

    private List<IntentTreeValidationIssueVO> validateForPublish(IntentTreeDefinition definition) {
        List<IntentTreeValidationIssueVO> issues = definitionValidator.validate(definition);
        if (Objects.isNull(definition) || Objects.isNull(definition.embeddingModelInstanceId())) {
            issues.add(new IntentTreeValidationIssueVO("EMBEDDING_MODEL_REQUIRED", "definition.embeddingModelInstanceId", "请选择Embedding模型"));
            return issues;
        }
        ModelInstanceReferenceBO model = modelInstanceService.findReference(definition.embeddingModelInstanceId()).orElse(null);
        if (Objects.isNull(model)) {
            issues.add(new IntentTreeValidationIssueVO("EMBEDDING_MODEL_NOT_FOUND", "definition.embeddingModelInstanceId", "Embedding模型不存在"));
        } else if (model.modelType() != ModelType.EMBEDDING) {
            issues.add(new IntentTreeValidationIssueVO("EMBEDDING_MODEL_TYPE_INVALID", "definition.embeddingModelInstanceId", "所选模型不是Embedding类型"));
        } else if (model.status() != EnableStatus.ACTIVE) {
            issues.add(new IntentTreeValidationIssueVO("EMBEDDING_MODEL_DISABLED", "definition.embeddingModelInstanceId", "Embedding模型已停用"));
        }
        return issues;
    }

    private IntentTreeEntity requireTree(Long id) {
        return intentTreeRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "意图树不存在"));
    }

    private IntentTreeDraftEntity requireDraft(Long intentTreeId) {
        return draftRepo.findByIntentTreeId(intentTreeId).orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "意图树草稿不存在"));
    }

    private IntentTreeVersionEntity requireVersion(Long intentTreeId, Long versionId) {
        return versionRepo.findByIntentTreeIdAndId(intentTreeId, versionId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "意图树发布版本不存在"));
    }

    /** 批量解析逻辑外键，并拒绝缺失或串树的生效版本。 */
    private Map<Long, IntentTreeVersionEntity> loadPublishedVersions(List<IntentTreeEntity> trees) {
        List<Long> versionIds = trees.stream()
                .map(IntentTreeEntity::getPublishedVersionId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, IntentTreeVersionEntity> versions = versionRepo.findAllByIds(versionIds).stream()
                .collect(Collectors.toMap(IntentTreeVersionEntity::getId, Function.identity(), (left, right) -> left));
        for (IntentTreeEntity tree : trees) {
            if (Objects.isNull(tree.getPublishedVersionId())) {
                continue;
            }
            IntentTreeVersionEntity version = versions.get(tree.getPublishedVersionId());
            if (Objects.isNull(version) || !Objects.equals(version.getIntentTreeId(), tree.getId())) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树生效版本关联异常");
            }
        }
        return versions;
    }

    private static IntentTreeDefinition defaultDefinition() {
        IntentNodeDefinition root = IntentNodeDefinition.builder()
                .id("root")
                .code("root")
                .name("全部意图")
                .description("意图树根节点")
                .nodeType(IntentNodeType.ROUTER)
                .positiveExamples(List.of())
                .negativeExamples(List.of())
                .slots(List.of())
                .priority(0)
                .enabled(true)
                .build();
        return new IntentTreeDefinition(1, null, new IntentRoutingPolicy(5, new BigDecimal("0.65"), new BigDecimal("0.08")), List.of(root));
    }

    private static String versionName(String requestedName, int versionNo) {
        return CharSequenceUtil.isBlank(requestedName) ? "V" + versionNo : requestedName.trim();
    }

    private static String trimToNull(String value) {
        return CharSequenceUtil.isBlank(value) ? null : value.trim();
    }
}
