package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentToolPolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftKnowledgeEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentEntity;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationIssueVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationResultVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.repo.DomainRepo;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeIndexVersionService;
import com.lingxi.workbench.admin.model.common.enums.ModelCapability;
import com.lingxi.workbench.admin.model.common.enums.ModelConnectionTestStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.repo.ModelInstanceRepo;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVariableDefinition;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.repo.PromptRepo;
import com.lingxi.workbench.admin.prompt.repo.PromptVersionRepo;
import com.lingxi.workbench.admin.tool.model.converter.ToolConverter;
import com.lingxi.workbench.admin.tool.repo.ToolRepo;
import com.lingxi.workbench.admin.tool.service.ToolService;
import com.lingxi.workbench.admin.tool.service.impl.ToolParameterSchemaFactory;
import com.lingxi.workbench.admin.tool.service.impl.ToolServiceImpl;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDraftValidatorTest {

    private static final long CHAT_MODEL_ID = 10L;
    private static final long EMBEDDING_MODEL_ID = 11L;
    private static final long SYSTEM_VERSION_ID = 20L;
    private static final long USER_VERSION_ID = 21L;

    private InMemoryModelRepo modelRepo;
    private InMemoryPromptRepo promptRepo;
    private InMemoryPromptVersionRepo promptVersionRepo;
    private InMemoryKnowledgeBaseRepo knowledgeBaseRepo;
    private StubKnowledgeIndexVersionService knowledgeIndexVersionService;
    private AgentDraftValidator validator;

    @BeforeEach
    void setUp() {
        modelRepo = new InMemoryModelRepo();
        promptRepo = new InMemoryPromptRepo();
        promptVersionRepo = new InMemoryPromptVersionRepo();
        knowledgeBaseRepo = new InMemoryKnowledgeBaseRepo();
        ToolParameterSchemaFactory parameterSchemaFactory = new ToolParameterSchemaFactory();
        ToolService toolService = new ToolServiceImpl(new InMemoryToolRepo(), new ToolConverter(parameterSchemaFactory), parameterSchemaFactory, null, null);
        knowledgeIndexVersionService = new StubKnowledgeIndexVersionService();
        DomainRepo domainRepo = mock(DomainRepo.class);
        DomainEntity domain = new DomainEntity();
        domain.setId(1L);
        domain.setRootId(1L);
        domain.setDepth(1);
        domain.setStatus(EnableStatus.ACTIVE);
        when(domainRepo.findById(anyLong())).thenReturn(Optional.of(domain));
        validator = new AgentDraftValidator(
                modelRepo,
                promptRepo,
                promptVersionRepo,
                knowledgeBaseRepo,
                Validation.buildDefaultValidatorFactory().getValidator(),
                toolService,
                knowledgeIndexVersionService,
                domainRepo);
        prepareModelsAndKnowledge();
        preparePrompt(
                SYSTEM_VERSION_ID,
                30L,
                PromptRole.SYSTEM,
                List.of());
    }

    @Test
    void shouldAcceptPublishedSystemAndUserPromptsWithRuntimeVariables() {
        preparePrompt(
                USER_VERSION_ID,
                31L,
                PromptRole.USER,
                specialistUserVariables());

        AgentValidationResultVO result = validator.validate(
                activeAgent(),
                validDraft(),
                null);

        assertTrue(result.valid());
    }

    @Test
    void shouldKeepUserControlledVariableOutOfAnswerSystemPrompt() {
        preparePrompt(
                SYSTEM_VERSION_ID,
                30L,
                PromptRole.SYSTEM,
                List.of(variable(
                        "user.query",
                        PromptVariableType.STRING,
                        true)));
        preparePrompt(
                USER_VERSION_ID,
                31L,
                PromptRole.USER,
                specialistUserVariables());

        AgentValidationResultVO result = validator.validate(
                activeAgent(),
                validDraft(),
                knowledgeBinding());
        List<String> issueCodes = result.issues().stream()
                .map(AgentValidationIssueVO::code)
                .toList();

        assertFalse(result.valid());
        assertTrue(issueCodes.contains("SYSTEM_PROMPT_VARIABLE_UNSAFE"));
    }

    @Test
    void shouldRejectWrongUserPromptRole() {
        preparePrompt(
                USER_VERSION_ID,
                31L,
                PromptRole.SYSTEM,
                List.of(variable("user.query", PromptVariableType.STRING, true)));

        AgentValidationResultVO result = validator.validate(
                activeAgent(),
                validDraft(),
                knowledgeBinding());
        List<String> issueCodes = result.issues().stream()
                .map(AgentValidationIssueVO::code)
                .toList();

        assertFalse(result.valid());
        assertTrue(issueCodes.contains("USER_PROMPT_ROLE_INVALID"));
    }

    @Test
    void shouldRequireToolCallingCapabilityWhenToolIsBound() {
        preparePrompt(
                USER_VERSION_ID,
                31L,
                PromptRole.USER,
                specialistUserVariables());
        AgentDraftEntity draft = validDraft();
        draft.setToolCodes(List.of("system_current_time"));

        AgentValidationResultVO result = validator.validate(
                activeAgent(),
                draft,
                knowledgeBinding());
        List<String> issueCodes = result.issues().stream()
                .map(AgentValidationIssueVO::code)
                .toList();

        assertFalse(result.valid());
        assertTrue(issueCodes.contains(
                "CHAT_MODEL_TOOL_CALLING_REQUIRED"));
    }

    @Test
    void shouldRejectCollaborationVariableForSpecialist() {
        preparePrompt(
                USER_VERSION_ID,
                31L,
                PromptRole.USER,
                List.of(
                        variable("user.query", PromptVariableType.STRING, true),
                        variable("collaboration.max_delegations", PromptVariableType.INTEGER, true)));

        AgentValidationResultVO result = validator.validate(
                activeAgent(AgentRole.SPECIALIST),
                validDraft(),
                null);
        List<String> issueCodes = result.issues().stream()
                .map(AgentValidationIssueVO::code)
                .toList();

        assertFalse(result.valid());
        assertTrue(issueCodes.contains("USER_PROMPT_VARIABLE_UNAVAILABLE"));
    }

    @Test
    void shouldAcceptSpecialistWithoutKnowledgeSearch() {
        preparePrompt(USER_VERSION_ID, 31L, PromptRole.USER, specialistUserVariables());
        AgentDraftEntity draft = validDraft();
        draft.setRetrievalPolicy(null);

        AgentValidationResultVO result = validator.validate(activeAgent(AgentRole.SPECIALIST), draft, null);

        assertTrue(result.valid());
    }

    @Test
    void shouldRequireToolCallingAndValidateIndexWhenSpecialistBindsKnowledge() {
        preparePrompt(USER_VERSION_ID, 31L, PromptRole.USER, specialistUserVariables());

        AgentValidationResultVO result = validator.validate(activeAgent(AgentRole.SPECIALIST), validDraft(), knowledgeBinding());
        List<String> issueCodes = result.issues().stream().map(AgentValidationIssueVO::code).toList();

        assertFalse(result.valid());
        assertTrue(issueCodes.contains("CHAT_MODEL_TOOL_CALLING_REQUIRED"));
        assertTrue(knowledgeIndexVersionService.wasValidated(40L, EMBEDDING_MODEL_ID));
    }

    @Test
    void shouldAcceptPublishableSpecialistKnowledgeSearch() {
        preparePrompt(USER_VERSION_ID, 31L, PromptRole.USER, specialistUserVariables());
        ModelInstanceEntity chatModel = model(CHAT_MODEL_ID, ModelType.CHAT, List.of(ModelCapability.STREAMING, ModelCapability.TOOL_CALLING));
        chatModel.setMaxTokens(4096);
        chatModel.setContextWindow(16000);
        modelRepo.put(chatModel);

        AgentValidationResultVO result = validator.validate(activeAgent(AgentRole.SPECIALIST), validDraft(), knowledgeBinding());

        assertTrue(result.valid());
        assertTrue(knowledgeIndexVersionService.wasValidated(40L, EMBEDDING_MODEL_ID));
    }

    @Test
    void shouldRejectRemovedKnowledgeContextVariableForSpecialist() {
        preparePrompt(USER_VERSION_ID, 31L, PromptRole.USER, userVariablesWithRemovedKnowledgeContext());

        AgentValidationResultVO result = validator.validate(activeAgent(AgentRole.SPECIALIST), validDraft(), null);
        List<String> issueCodes = result.issues().stream().map(AgentValidationIssueVO::code).toList();

        assertFalse(result.valid());
        assertTrue(issueCodes.contains("USER_PROMPT_VARIABLE_UNSUPPORTED"));
    }

    @Test
    void shouldReturnKnowledgeIndexFailureAsValidationIssue() {
        preparePrompt(USER_VERSION_ID, 31L, PromptRole.USER, specialistUserVariables());
        ModelInstanceEntity chatModel = model(CHAT_MODEL_ID, ModelType.CHAT, List.of(ModelCapability.STREAMING, ModelCapability.TOOL_CALLING));
        chatModel.setMaxTokens(4096);
        chatModel.setContextWindow(16000);
        modelRepo.put(chatModel);
        knowledgeIndexVersionService.failWith("知识索引向量缺失或维度不一致");

        AgentValidationResultVO result = validator.validate(activeAgent(AgentRole.SPECIALIST), validDraft(), knowledgeBinding());
        List<String> issueCodes = result.issues().stream().map(AgentValidationIssueVO::code).toList();

        assertFalse(result.valid());
        assertTrue(issueCodes.contains("KNOWLEDGE_INDEX_UNPUBLISHABLE"));
    }

    @Test
    void shouldAcceptValidSupervisorContract() {
        AgentDraftEntity draft = validDraft();
        draft.setUserPromptVersionId(null);
        draft.setPlanPolicy(PlanPolicy.defaults());

        AgentValidationResultVO result = validator.validate(
                activeAgent(AgentRole.SUPERVISOR),
                draft,
                null);

        assertTrue(result.valid());
    }

    @Test
    void shouldRejectSupervisorUserPromptToolsKnowledgeAndInvalidPolicy() {
        AgentDraftEntity draft = validDraft();
        draft.setToolCodes(List.of("system_current_time"));
        draft.setPlanPolicy(PlanPolicy.builder()
                .maxSteps(21)
                .maxReplans(4)
                .maxProtocolRepairAttempts(4)
                .build());

        AgentValidationResultVO result = validator.validate(
                activeAgent(AgentRole.SUPERVISOR),
                draft,
                knowledgeBinding());
        List<String> issueCodes = result.issues().stream()
                .map(AgentValidationIssueVO::code)
                .toList();

        assertFalse(result.valid());
        assertTrue(issueCodes.contains("USER_PROMPT_UNSUPPORTED"));
        assertTrue(issueCodes.contains("TOOL_CODES_UNSUPPORTED"));
        assertTrue(issueCodes.contains("KNOWLEDGE_UNSUPPORTED"));
        assertTrue(issueCodes.contains("PLAN_POLICY_INVALID"));
    }

    @Test
    void shouldRejectPlanPolicyOnConversationalAgent() {
        preparePrompt(
                USER_VERSION_ID,
                31L,
                PromptRole.USER,
                specialistUserVariables());
        AgentDraftEntity draft = validDraft();
        draft.setPlanPolicy(PlanPolicy.defaults());

        AgentValidationResultVO result = validator.validate(
                activeAgent(AgentRole.SPECIALIST),
                draft,
                null);

        assertFalse(result.valid());
        assertTrue(result.issues().stream()
                .map(AgentValidationIssueVO::code)
                .anyMatch("PLAN_POLICY_UNSUPPORTED"::equals));
    }

    private void prepareModelsAndKnowledge() {
        ModelInstanceEntity chatModel = model(
                CHAT_MODEL_ID,
                ModelType.CHAT,
                List.of(ModelCapability.STREAMING));
        chatModel.setMaxTokens(4096);
        chatModel.setContextWindow(16000);
        modelRepo.put(chatModel);
        modelRepo.put(model(
                EMBEDDING_MODEL_ID,
                ModelType.EMBEDDING,
                List.of()));

        KnowledgeBaseEntity knowledgeBase = new KnowledgeBaseEntity();
        knowledgeBase.setId(40L);
        knowledgeBase.setEmbeddingModelInstanceId(EMBEDDING_MODEL_ID);
        knowledgeBase.setStatus(EnableStatus.ACTIVE);
        knowledgeBaseRepo.put(knowledgeBase);
    }

    private void preparePrompt(
            long versionId,
            long promptId,
            PromptRole role,
            List<PromptVariableDefinition> variables) {
        PromptVersionEntity version = new PromptVersionEntity();
        version.setId(versionId);
        version.setPromptId(promptId);
        version.setStatus(PromptVersionStatus.PUBLISHED);
        version.setVariables(variables);
        promptVersionRepo.put(version);

        PromptEntity prompt = new PromptEntity();
        prompt.setId(promptId);
        prompt.setRole(role);
        prompt.setStatus(EnableStatus.ACTIVE);
        promptRepo.put(prompt);
    }

    private static ModelInstanceEntity model(
            long id,
            ModelType type,
            List<ModelCapability> capabilities) {
        ModelInstanceEntity model = new ModelInstanceEntity();
        model.setId(id);
        model.setModelType(type);
        model.setCapabilities(capabilities);
        model.setStatus(EnableStatus.ACTIVE);
        model.setLastTestStatus(ModelConnectionTestStatus.SUCCESS);
        return model;
    }

    private static AgentEntity activeAgent() {
        return activeAgent(AgentRole.SPECIALIST);
    }

    private static AgentEntity activeAgent(AgentRole role) {
        AgentEntity agent = new AgentEntity();
        agent.setStatus(EnableStatus.ACTIVE);
        agent.setRole(role);
        if (role == AgentRole.SPECIALIST) {
            agent.setDomainId(1L);
        }
        return agent;
    }

    private static AgentDraftEntity validDraft() {
        AgentDraftEntity draft = new AgentDraftEntity();
        draft.setSchemaVersion(AGENT_SCHEMA_VERSION);
        draft.setChatModelInstanceId(CHAT_MODEL_ID);
        draft.setSystemPromptVersionId(SYSTEM_VERSION_ID);
        draft.setUserPromptVersionId(USER_VERSION_ID);
        draft.setRetrievalPolicy(RetrievalPolicy.defaults());
        draft.setGenerationPolicy(GenerationPolicy.defaults());
        draft.setToolCodes(List.of());
        draft.setToolPolicy(AgentToolPolicy.defaults());
        return draft;
    }

    private static AgentDraftKnowledgeEntity knowledgeBinding() {
        AgentDraftKnowledgeEntity binding = new AgentDraftKnowledgeEntity();
        binding.setKnowledgeBaseId(40L);
        binding.setEmbeddingModelInstanceId(EMBEDDING_MODEL_ID);
        return binding;
    }

    private static List<PromptVariableDefinition> userVariablesWithRemovedKnowledgeContext() {
        return List.of(
                variable("user.query", PromptVariableType.STRING, true),
                variable("knowledge.context", PromptVariableType.STRING, false));
    }

    private static List<PromptVariableDefinition> specialistUserVariables() {
        return List.of(variable("user.query", PromptVariableType.STRING, true));
    }

    private static final class StubKnowledgeIndexVersionService extends KnowledgeIndexVersionService {
        private Long validatedKnowledgeBaseId;
        private Long validatedEmbeddingModelId;
        private String failureMessage;

        private StubKnowledgeIndexVersionService() {
            super(null, null, null, null, null);
        }

        @Override
        public void validatePublishable(Long knowledgeBaseId, Long embeddingModelInstanceId) {
            validatedKnowledgeBaseId = knowledgeBaseId;
            validatedEmbeddingModelId = embeddingModelInstanceId;
            if (Objects.nonNull(failureMessage)) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, failureMessage);
            }
        }

        private boolean wasValidated(Long knowledgeBaseId, Long embeddingModelInstanceId) {
            return knowledgeBaseId.equals(validatedKnowledgeBaseId)
                    && embeddingModelInstanceId.equals(validatedEmbeddingModelId);
        }

        private void failWith(String message) {
            failureMessage = message;
        }
    }

    private static PromptVariableDefinition variable(
            String name,
            PromptVariableType type,
            boolean required) {
        return PromptVariableDefinition.builder()
                .name(name)
                .type(type)
                .required(required)
                .build();
    }

    /**
     * 校验器单元测试只关心资源查询契约，因此使用内存仓储，
     * 避免测试结果依赖数据库或 JVM 动态代理能力。
     */
    private static final class InMemoryModelRepo extends ModelInstanceRepo {
        private final Map<Long, ModelInstanceEntity> models = new HashMap<>();

        private InMemoryModelRepo() {
            super(null);
        }

        private void put(ModelInstanceEntity model) {
            models.put(model.getId(), model);
        }

        @Override
        public Optional<ModelInstanceEntity> findById(Long id) {
            return Optional.ofNullable(models.get(id));
        }
    }

    private static final class InMemoryPromptRepo extends PromptRepo {
        private final Map<Long, PromptEntity> prompts = new HashMap<>();

        private InMemoryPromptRepo() {
            super(null);
        }

        private void put(PromptEntity prompt) {
            prompts.put(prompt.getId(), prompt);
        }

        @Override
        public Optional<PromptEntity> findById(Long id) {
            return Optional.ofNullable(prompts.get(id));
        }
    }

    private static final class InMemoryPromptVersionRepo extends PromptVersionRepo {
        private final Map<Long, PromptVersionEntity> versions = new HashMap<>();

        private InMemoryPromptVersionRepo() {
            super(null);
        }

        private void put(PromptVersionEntity version) {
            versions.put(version.getId(), version);
        }

        @Override
        public Optional<PromptVersionEntity> findById(Long id) {
            return Optional.ofNullable(versions.get(id));
        }
    }

    private static final class InMemoryKnowledgeBaseRepo extends KnowledgeBaseRepo {
        private final Map<Long, KnowledgeBaseEntity> knowledgeBases = new HashMap<>();

        private InMemoryKnowledgeBaseRepo() {
            super(null);
        }

        private void put(KnowledgeBaseEntity knowledgeBase) {
            knowledgeBases.put(knowledgeBase.getId(), knowledgeBase);
        }

        @Override
        public Optional<KnowledgeBaseEntity> findById(Long id) {
            return Optional.ofNullable(knowledgeBases.get(id));
        }
    }

    /** 仅暴露发布校验需要的启用 Tool 编码。 */
    private static final class InMemoryToolRepo extends ToolRepo {

        private InMemoryToolRepo() {
            super(null);
        }

        @Override
        public Set<String> findActiveCodes(List<String> codes) {
            return codes.contains("system_current_time") ? Set.of("system_current_time") : Set.of();
        }
    }
}
