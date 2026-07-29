package com.lingxi.workbench.admin.agent.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.agent.common.enums.KnowledgeBindingMode;
import com.lingxi.workbench.admin.agent.model.config.AgentDefinition;
import com.lingxi.workbench.admin.agent.model.config.AgentKnowledgeBinding;
import com.lingxi.workbench.admin.agent.model.config.AgentMemoryPolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentRuntimePolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.QueryRewritePolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import com.lingxi.workbench.admin.agent.model.converter.AgentConverter;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftKnowledgeEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentVersionEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentVersionKnowledgeEntity;
import com.lingxi.workbench.admin.agent.model.request.AgentPageQuery;
import com.lingxi.workbench.admin.agent.model.request.CreateAgentRequest;
import com.lingxi.workbench.admin.agent.model.request.PublishAgentRequest;
import com.lingxi.workbench.admin.agent.model.request.SaveAgentDraftRequest;
import com.lingxi.workbench.admin.agent.model.request.UpdateAgentRequest;
import com.lingxi.workbench.admin.agent.model.vo.AgentDetailVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationIssueVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationResultVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVersionDetailVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentVersionVO;
import com.lingxi.workbench.admin.agent.repo.AgentDraftKnowledgeRepo;
import com.lingxi.workbench.admin.agent.repo.AgentDraftRepo;
import com.lingxi.workbench.admin.agent.repo.AgentRepo;
import com.lingxi.workbench.admin.agent.repo.AgentVersionKnowledgeRepo;
import com.lingxi.workbench.admin.agent.repo.AgentVersionRepo;
import com.lingxi.workbench.admin.agent.service.AgentDraftValidator;
import com.lingxi.workbench.admin.agent.service.AgentService;
import com.lingxi.workbench.admin.agent.service.AgentSnapshotChecksum;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Agent 控制面业务实现。 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
    private static final int AGENT_SCHEMA_VERSION = 2;

    private final AgentRepo agentRepo;
    private final AgentDraftRepo draftRepo;
    private final AgentDraftKnowledgeRepo draftKnowledgeRepo;
    private final AgentVersionRepo versionRepo;
    private final AgentVersionKnowledgeRepo versionKnowledgeRepo;
    private final KnowledgeBaseRepo knowledgeBaseRepo;
    private final AgentDraftValidator draftValidator;
    private final AgentSnapshotChecksum snapshotChecksum;
    private final AgentConverter converter;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    /** 分页查询 Agent，同时填充草稿 revision 和生效版本号到列表 VO。 */
    public PageResult<AgentVO> page(AgentPageQuery query) {
        PageResult<AgentEntity> page = agentRepo.findPage(query);
        List<Long> agentIds = page.records().stream().map(AgentEntity::getId).toList();
        Map<Long, AgentDraftEntity> drafts = draftRepo.findAllByAgentIds(agentIds).stream()
                .collect(Collectors.toMap(AgentDraftEntity::getAgentId, Function.identity()));
        Map<Long, AgentVersionEntity> versions = versionRepo.findAllByAgentIds(agentIds).stream()
                .collect(Collectors.toMap(
                        AgentVersionEntity::getId,
                        Function.identity(),
                        (left, right) -> left));
        return page.map(agent -> converter.toListVO(
                agent,
                versions.get(agent.getPublishedVersionId()),
                drafts.get(agent.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    /** 查看 Agent 编辑详情，组装主记录、草稿、知识库绑定和生效版本。 */
    public AgentDetailVO detail(Long id) {
        AgentEntity agent = requireAgent(id);
        AgentDraftEntity draft = requireDraft(id);
        AgentDraftKnowledgeEntity knowledge =
                draftKnowledgeRepo.findByDraftId(draft.getId()).orElse(null);
        AgentVersionEntity publishedVersion = Objects.isNull(agent.getPublishedVersionId())
                ? null
                : versionRepo.findById(agent.getPublishedVersionId()).orElse(null);
        return converter.toDetailVO(agent, publishedVersion, draft, knowledge);
    }

    @Override
    @Transactional(readOnly = true)
    /** 查看 Agent 历史发布版本列表。 */
    public List<AgentVersionVO> versions(Long id) {
        requireAgent(id);
        return versionRepo.findAll(id).stream().map(converter::toVersionVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    /** 查看指定发布版本详情，含完整定义快照。 */
    public AgentVersionDetailVO versionDetail(Long id, Long versionId) {
        requireAgent(id);
        return converter.toVersionDetailVO(requireVersion(id, versionId));
    }

    @Override
    @Transactional
    /** 创建 Agent，同时初始化各策略默认值的草稿，记录审计。 */
    public AgentDetailVO create(CreateAgentRequest request, Long operatorId) {
        String code = request.getCode().trim().toLowerCase();
        if (agentRepo.existsByCode(code, null)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Agent编码已存在");
        }
        AgentEntity agent = new AgentEntity();
        agent.setName(request.getName().trim());
        agent.setCode(code);
        agent.setDescription(trimToNull(request.getDescription()));
        agent.setAgentType(request.getAgentType());
        agent.setStatus(EnableStatus.ACTIVE);
        agent.setCreatedBy(operatorId);
        agent.setUpdatedBy(operatorId);
        agentRepo.save(agent);

        AgentDraftEntity draft = new AgentDraftEntity();
        draft.setAgentId(agent.getId());
        draft.setSchemaVersion(AGENT_SCHEMA_VERSION);
        draft.setMemoryPolicy(AgentMemoryPolicy.defaults());
        draft.setRewritePolicy(QueryRewritePolicy.defaults());
        draft.setRetrievalPolicy(RetrievalPolicy.defaults());
        draft.setGenerationPolicy(GenerationPolicy.defaults());
        draft.setRuntimePolicy(AgentRuntimePolicy.defaults());
        draft.setRevision(0L);
        draft.setCreatedBy(operatorId);
        draft.setUpdatedBy(operatorId);
        draftRepo.insert(draft);

        auditService.record(
                "AGENT", "AGENT", agent.getId(), operatorId, "AGENT_CREATE",
                "Agent编码=" + code + "，类型=" + request.getAgentType().getCode());
        return detail(agent.getId());
    }

    @Override
    @Transactional
    /** 修改 Agent 名称和描述元信息。 */
    public AgentDetailVO update(
            Long id, UpdateAgentRequest request, Long operatorId) {
        AgentEntity agent = requireAgent(id);
        agent.setName(request.getName().trim());
        agent.setDescription(trimToNull(request.getDescription()));
        agent.setUpdatedBy(operatorId);
        agentRepo.save(agent);
        auditService.record(
                "AGENT", "AGENT", id, operatorId, "AGENT_UPDATE",
                "Agent编码=" + agent.getCode());
        return detail(id);
    }

    @Override
    @Transactional
    /** 保存 Agent 草稿配置，以 revision 做乐观锁防止并发覆盖，同步更新知识库绑定。 */
    public AgentDetailVO saveDraft(
            Long id, SaveAgentDraftRequest request, Long operatorId) {
        requireAgent(id);
        AgentDraftEntity draft = draftRepo.findByAgentIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "Agent草稿不存在"));
        if (!Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "Agent草稿已被其他操作修改，请刷新后重试");
        }

        draft.setChatModelInstanceId(request.getChatModelInstanceId());
        draft.setRewriteModelInstanceId(request.getRewriteModelInstanceId());
        draft.setSystemPromptVersionId(request.getSystemPromptVersionId());
        draft.setUserPromptVersionId(request.getUserPromptVersionId());
        draft.setRewritePromptVersionId(request.getRewritePromptVersionId());
        draft.setMemoryPolicy(request.getMemoryPolicy());
        draft.setRewritePolicy(request.getRewritePolicy());
        draft.setRetrievalPolicy(request.getRetrievalPolicy());
        draft.setGenerationPolicy(request.getGenerationPolicy());
        draft.setRuntimePolicy(request.getRuntimePolicy());
        draft.setChangeNote(trimToNull(request.getChangeNote()));
        draft.setUpdatedBy(operatorId);
        if (!draftRepo.updateWithRevision(draft, request.getExpectedRevision())) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "Agent草稿已被其他操作修改，请刷新后重试");
        }
        saveKnowledgeBinding(draft, request.getKnowledgeBaseId());
        auditService.record(
                "AGENT", "AGENT_DRAFT", draft.getId(), operatorId, "AGENT_DRAFT_SAVE",
                "Agent主键=" + id + "，revision=" + (request.getExpectedRevision() + 1));
        return detail(id);
    }

    @Override
    @Transactional(readOnly = true)
    /** 对当前草稿执行发布前逐项校验，返回每个检查点的通过/失败详情。 */
    public AgentValidationResultVO validate(Long id) {
        AgentEntity agent = requireAgent(id);
        AgentDraftEntity draft = requireDraft(id);
        AgentDraftKnowledgeEntity knowledge =
                draftKnowledgeRepo.findByDraftId(draft.getId()).orElse(null);
        return draftValidator.validate(agent, draft, knowledge);
    }

    @Override
    @Transactional
    /** 校验草稿、构建定义快照并生成 SHA-256，去重后创建不可变版本并标记为当前生效版本。 */
    public AgentVersionDetailVO publish(
            Long id, PublishAgentRequest request, Long operatorId) {
        AgentEntity agent = agentRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "Agent不存在"));
        AgentDraftEntity draft = draftRepo.findByAgentIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "Agent草稿不存在"));
        if (!Objects.equals(draft.getRevision(), request.getExpectedRevision())) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "Agent草稿已变化，请重新校验后发布");
        }
        AgentDraftKnowledgeEntity knowledge = draftKnowledgeRepo
                .findByDraftId(draft.getId())
                .orElse(null);
        AgentValidationResultVO validation = draftValidator.validate(agent, draft, knowledge);
        if (!validation.valid()) {
            String message = validation.issues().stream()
                    .map(AgentValidationIssueVO::message)
                    .collect(Collectors.joining("；"));
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Agent发布校验失败：" + message);
        }

        AgentDefinition definition = buildDefinition(agent, draft, knowledge);
        String checksum = snapshotChecksum.calculate(definition);
        if (versionRepo.existsByChecksum(id, checksum)) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT, "当前草稿与已发布版本完全相同，无需重复发布");
        }
        int versionNo = versionRepo.nextVersionNo(id);
        OffsetDateTime now = OffsetDateTime.now();
        AgentVersionEntity version = new AgentVersionEntity();
        version.setAgentId(id);
        version.setVersionNo(versionNo);
        version.setVersionName(versionName(request.getVersionName(), versionNo));
        version.setSchemaVersion(draft.getSchemaVersion());
        version.setDefinitionSnapshot(definition);
        version.setSnapshotChecksum(checksum);
        version.setChangeNote(draft.getChangeNote());
        version.setPublishedBy(operatorId);
        version.setPublishedAt(now);
        versionRepo.insert(version);
        insertVersionKnowledge(version.getId(), knowledge);

        agent.setPublishedVersionId(version.getId());
        agent.setUpdatedBy(operatorId);
        agentRepo.save(agent);
        draftRepo.updatePublicationBase(
                draft.getId(), version.getId(), operatorId, request.getExpectedRevision());
        auditService.record(
                "AGENT", "AGENT", id, operatorId, "AGENT_PUBLISH",
                "Agent编码=" + agent.getCode() + "，发布版本=V" + versionNo
                        + "，checksum=" + checksum);
        return converter.toVersionDetailVO(version);
    }

    @Override
    @Transactional
    /** 将 Agent 当前生效版本切换为指定历史版本。 */
    public void activateVersion(
            Long id, Long versionId, Long operatorId) {
        AgentEntity agent = agentRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "Agent不存在"));
        if (agent.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "停用的Agent不能切换版本");
        }
        AgentVersionEntity version = requireVersion(id, versionId);
        agent.setPublishedVersionId(versionId);
        agent.setUpdatedBy(operatorId);
        agentRepo.save(agent);
        auditService.record(
                "AGENT", "AGENT", id, operatorId, "AGENT_VERSION_ACTIVATE",
                "Agent编码=" + agent.getCode() + "，生效版本=V" + version.getVersionNo());
    }

    @Override
    @Transactional
    /** 启用或停用 Agent，记录操作审计。 */
    public void changeStatus(Long id, EnableStatus status, Long operatorId) {
        AgentEntity agent = requireAgent(id);
        agent.setStatus(status);
        agent.setUpdatedBy(operatorId);
        agentRepo.save(agent);
        auditService.record(
                "AGENT", "AGENT", id, operatorId, "AGENT_STATUS_CHANGE",
                "Agent编码=" + agent.getCode() + "，状态=" + status.getCode());
    }

    private void saveKnowledgeBinding(AgentDraftEntity draft, Long knowledgeBaseId) {
        if (Objects.isNull(knowledgeBaseId)) {
            draftKnowledgeRepo.deleteByDraftId(draft.getId());
            return;
        }
        KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepo.findById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "知识库不存在"));
        if (Objects.isNull(knowledgeBase.getEmbeddingModelInstanceId())) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST, "知识库尚未配置Embedding模型");
        }
        AgentDraftKnowledgeEntity binding = draftKnowledgeRepo.findByDraftId(draft.getId())
                .orElseGet(AgentDraftKnowledgeEntity::new);
        binding.setAgentDraftId(draft.getId());
        binding.setKnowledgeBaseId(knowledgeBaseId);
        binding.setEmbeddingModelInstanceId(knowledgeBase.getEmbeddingModelInstanceId());
        binding.setBindingMode(KnowledgeBindingMode.LATEST);
        binding.setPriority(0);
        draftKnowledgeRepo.save(binding);
    }

    private static AgentDefinition buildDefinition(
            AgentEntity agent,
            AgentDraftEntity draft,
            AgentDraftKnowledgeEntity knowledge) {
        return AgentDefinition.builder()
                .schemaVersion(draft.getSchemaVersion())
                .agentType(agent.getAgentType())
                .chatModelInstanceId(draft.getChatModelInstanceId())
                .rewriteModelInstanceId(draft.getRewriteModelInstanceId())
                .systemPromptVersionId(draft.getSystemPromptVersionId())
                .userPromptVersionId(draft.getUserPromptVersionId())
                .rewritePromptVersionId(draft.getRewritePromptVersionId())
                .knowledge(AgentKnowledgeBinding.builder()
                        .knowledgeBaseId(knowledge.getKnowledgeBaseId())
                        .embeddingModelInstanceId(knowledge.getEmbeddingModelInstanceId())
                        .bindingMode(knowledge.getBindingMode())
                        .priority(knowledge.getPriority())
                        .build())
                .memoryPolicy(draft.getMemoryPolicy())
                .rewritePolicy(draft.getRewritePolicy())
                .retrievalPolicy(draft.getRetrievalPolicy())
                .generationPolicy(draft.getGenerationPolicy())
                .runtimePolicy(draft.getRuntimePolicy())
                .build();
    }

    private void insertVersionKnowledge(
            Long versionId, AgentDraftKnowledgeEntity knowledge) {
        AgentVersionKnowledgeEntity versionKnowledge = new AgentVersionKnowledgeEntity();
        versionKnowledge.setAgentVersionId(versionId);
        versionKnowledge.setKnowledgeBaseId(knowledge.getKnowledgeBaseId());
        versionKnowledge.setEmbeddingModelInstanceId(knowledge.getEmbeddingModelInstanceId());
        versionKnowledge.setBindingMode(knowledge.getBindingMode());
        versionKnowledge.setPriority(knowledge.getPriority());
        versionKnowledgeRepo.insert(versionKnowledge);
    }

    private AgentEntity requireAgent(Long id) {
        return agentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "Agent不存在"));
    }

    private AgentDraftEntity requireDraft(Long agentId) {
        return draftRepo.findByAgentId(agentId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.CONFLICT, "Agent草稿不存在"));
    }

    private AgentVersionEntity requireVersion(Long agentId, Long versionId) {
        return versionRepo.findByAgentIdAndId(agentId, versionId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatusEnum.NOT_FOUND, "Agent发布版本不存在"));
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
