package com.lingxi.workbench.admin.intent.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.domain.service.DomainService;
import com.lingxi.workbench.admin.intent.model.converter.IntentTreeConverter;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeDraftEntity;
import com.lingxi.workbench.admin.intent.model.entity.IntentTreeEntity;
import com.lingxi.workbench.admin.intent.model.vo.IntentTreeValidationResultVO;
import com.lingxi.workbench.admin.intent.repo.IntentTreeDraftRepo;
import com.lingxi.workbench.admin.intent.repo.IntentTreeRepo;
import com.lingxi.workbench.admin.intent.repo.IntentTreeVectorRepo;
import com.lingxi.workbench.admin.intent.repo.IntentTreeVersionRepo;
import com.lingxi.workbench.admin.intent.service.IntentTreeDefinitionValidator;
import com.lingxi.workbench.admin.intent.service.IntentTreeSnapshotChecksum;
import com.lingxi.workbench.admin.intent.service.IntentVectorSourceBuilder;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeEmbeddingModelResolver;
import com.lingxi.workbench.admin.model.common.enums.ModelConnectionTestStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.bo.ModelInstanceReferenceBO;
import com.lingxi.workbench.admin.model.service.ModelInstanceService;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.service.PromptService;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntentTreeServiceImplTest {

    @Mock
    private IntentTreeRepo intentTreeRepo;
    @Mock
    private IntentTreeDraftRepo draftRepo;
    @Mock
    private IntentTreeVersionRepo versionRepo;
    @Mock
    private IntentTreeDefinitionValidator definitionValidator;
    @Mock
    private IntentTreeSnapshotChecksum snapshotChecksum;
    @Mock
    private IntentTreeConverter converter;
    @Mock
    private ModelInstanceService modelInstanceService;
    @Mock
    private PromptService promptService;
    @Mock
    private KnowledgeEmbeddingModelResolver embeddingModelResolver;
    @Mock
    private EmbeddingGateway embeddingGateway;
    @Mock
    private IntentVectorSourceBuilder vectorSourceBuilder;
    @Mock
    private IntentTreeVectorRepo vectorRepo;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private AuditService auditService;
    @Mock
    private DomainService domainService;

    @InjectMocks
    private IntentTreeServiceImpl service;

    @BeforeEach
    void setUp() {
        IntentTreeEntity tree = new IntentTreeEntity();
        tree.setId(1L);
        tree.setDomainId(2L);
        IntentTreeDraftEntity draft = new IntentTreeDraftEntity();
        draft.setIntentTreeId(1L);
        draft.setDefinition(definition());
        when(intentTreeRepo.findById(1L)).thenReturn(Optional.of(tree));
        when(draftRepo.findByIntentTreeId(1L)).thenReturn(Optional.of(draft));
        when(definitionValidator.validate(draft.getDefinition())).thenReturn(List.of());
        when(modelInstanceService.findReference(8L)).thenReturn(Optional.of(model(8L, ModelType.EMBEDDING, "OPENAI_COMPATIBLE", ModelConnectionTestStatus.SUCCESS)));
        when(modelInstanceService.findReference(9L)).thenReturn(Optional.of(model(9L, ModelType.CHAT, "OPENAI_COMPATIBLE", ModelConnectionTestStatus.SUCCESS)));
        when(promptService.findPublishedVersion(10L)).thenReturn(Optional.of(new PromptService.PromptVersionReference(
                10L, PromptRole.SYSTEM, true,
                List.of(new PromptService.PromptVariableReference("system.current_date", PromptVariableType.DATE)))));
    }

    @Test
    void shouldAcceptAvailableDecisionDependencies() {
        IntentTreeValidationResultVO result = service.validate(1L);

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void shouldRejectUnsupportedDecisionModelProtocolAndFailedConnection() {
        when(modelInstanceService.findReference(9L)).thenReturn(Optional.of(
                model(9L, ModelType.CHAT, "CUSTOM", ModelConnectionTestStatus.FAILED)));

        IntentTreeValidationResultVO result = service.validate(1L);

        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("DECISION_MODEL_PROTOCOL_UNSUPPORTED");
    }

    @Test
    void shouldRejectUnsafeDecisionPromptVariable() {
        when(promptService.findPublishedVersion(10L)).thenReturn(Optional.of(new PromptService.PromptVersionReference(
                10L, PromptRole.SYSTEM, true,
                List.of(new PromptService.PromptVariableReference("user.id", PromptVariableType.LONG)))));

        IntentTreeValidationResultVO result = service.validate(1L);

        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("DECISION_PROMPT_VARIABLE_UNSAFE");
    }

    @Test
    void shouldRejectUserQueryInDecisionSystemPrompt() {
        when(promptService.findPublishedVersion(10L)).thenReturn(Optional.of(new PromptService.PromptVersionReference(
                10L, PromptRole.SYSTEM, true,
                List.of(new PromptService.PromptVariableReference("user.query", PromptVariableType.STRING)))));

        IntentTreeValidationResultVO result = service.validate(1L);

        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("DECISION_PROMPT_VARIABLE_UNSAFE");
    }

    @Test
    void shouldRejectNonSystemDecisionPrompt() {
        when(promptService.findPublishedVersion(10L)).thenReturn(Optional.of(new PromptService.PromptVersionReference(
                10L, PromptRole.USER, true, List.of())));

        IntentTreeValidationResultVO result = service.validate(1L);

        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("DECISION_PROMPT_ROLE_INVALID");
    }

    private static IntentTreeDefinition definition() {
        IntentRoutingPolicy policy = IntentRoutingPolicy.builder()
                .topK(5)
                .recallThreshold(new BigDecimal("0.65"))
                .chatModelInstanceId(9L)
                .decisionPromptVersionId(10L)
                .maxOutputTokens(512)
                .timeoutSeconds(30)
                .maxRepeatAttempts(1)
                .decisionFailureAction(IntentDecisionFailureAction.DEFER)
                .build();
        return new IntentTreeDefinition(INTENT_TREE_SCHEMA_VERSION, 8L, policy, List.of());
    }

    private static ModelInstanceReferenceBO model(Long id, ModelType type, String protocol,
                                                   ModelConnectionTestStatus testStatus) {
        return ModelInstanceReferenceBO.builder()
                .id(id)
                .modelType(type)
                .status(EnableStatus.ACTIVE)
                .protocol(protocol)
                .capabilities(List.of())
                .lastTestStatus(testStatus)
                .build();
    }
}
