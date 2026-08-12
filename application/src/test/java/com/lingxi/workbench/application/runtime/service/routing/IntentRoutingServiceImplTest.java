package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentVectorRuntimeRepo;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

class IntentRoutingServiceImplTest {

    private final IntentTreeRuntimeRepo intentTreeRepo = mock(IntentTreeRuntimeRepo.class);
    private final IntentVectorRuntimeRepo vectorRepo = mock(IntentVectorRuntimeRepo.class);
    private final IntentRoutingSessionRepo sessionRepo = mock(IntentRoutingSessionRepo.class);
    private final ModelRuntimeConfigService modelConfigService = mock(ModelRuntimeConfigService.class);
    private final EmbeddingGateway embeddingGateway = mock(EmbeddingGateway.class);
    private final IntentRecognitionServiceImpl recognitionService = new IntentRecognitionServiceImpl(
            intentTreeRepo, vectorRepo, modelConfigService, embeddingGateway);
    private final IntentRoutingServiceImpl service = new IntentRoutingServiceImpl(
            intentTreeRepo, recognitionService, sessionRepo);

    @BeforeEach
    void setUp() {
        when(sessionRepo.findPending(10L, 7L, 30L)).thenReturn(Optional.empty());
        when(modelConfigService.getEmbeddingModelConfig(88L))
                .thenReturn(EmbeddingModelRuntimeConfig.builder().instanceId(88L).build());
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[]{1F, 0F}));
    }

    @Test
    void shouldReturnNoMatchWhenNoCandidatePassesThreshold() {
        stubVersion(List.of(leaf("refund", "申请退款")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of());

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "我想咨询");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.NO_MATCH);
        assertThat(result.getExecutionQuery()).isEqualTo("我想咨询");
    }

    @Test
    void shouldRouteHighConfidenceLeafToPublishedWorkflow() {
        stubVersion(List.of(leaf("refund", "申请退款")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.91)));

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "我要退款");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.EXECUTE_WORKFLOW);
        assertThat(result.getIntentCode()).isEqualTo("refund");
        assertThat(result.getExecutionQuery()).isEqualTo("我要退款");
    }

    @Test
    void shouldUseContextualizedQueryForFreshRouting() {
        stubVersion(List.of(leaf("refund", "申请退款")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.91)));
        AppRuntimeSnapshot snapshot = optionalSnapshot(List.of(optionalWorkflowMapping("refund")));
        LoginUser loginUser = new LoginUser(7L, "tester", "测试用户", null);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);
        ConversationContext context = context(snapshot, loginUser, turn, "它可以退吗", "iPhone 订单可以退款吗");

        IntentRoutingResult result = service.route(context, null);

        assertThat(result.getExecutionQuery()).isEqualTo("iPhone 订单可以退款吗");
        org.mockito.Mockito.verify(embeddingGateway).embed(eq(List.of("iPhone 订单可以退款吗")), any());
    }

    @Test
    void shouldPersistClarificationWhenTopCandidatesAreAmbiguous() {
        stubVersion(List.of(leaf("refund", "申请退款"), leaf("exchange", "申请换货")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of(
                new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.91),
                new IntentVectorRuntimeRepo.IntentCandidateRow("exchange-node", "exchange", 0.87)));
        doAnswer(invocation -> {
            IntentRoutingSessionEntity session = invocation.getArgument(0);
            session.setId(99L);
            return null;
        }).when(sessionRepo).insert(any());

        IntentRoutingResult result = route(optionalSnapshot(List.of(
                optionalWorkflowMapping("refund"), optionalWorkflowMapping("exchange"))), "订单有问题");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.CLARIFICATION);
        assertThat(result.getClarification().getClarificationId()).isEqualTo("99");
        assertThat(result.getClarification().getOptions())
                .extracting(ChatClarificationEventBO.ClarificationOptionBO::code)
                .containsExactly("refund", "exchange");
    }

    @Test
    void shouldResumeAmbiguityWithOriginalTaskQuery() {
        stubVersion(List.of(leaf("refund", "申请退款"), leaf("exchange", "申请换货")));
        IntentRoutingSessionEntity session = pendingSession(
                99L,
                IntentClarificationType.INTENT_DISAMBIGUATION,
                IntentClarificationContext.builder()
                        .originalQuery("订单商品有问题")
                        .candidateCodes(List.of("refund", "exchange"))
                        .collectedSlots(new LinkedHashMap<>())
                        .build());
        when(sessionRepo.findOwned(99L, 10L, 7L)).thenReturn(Optional.of(session));
        ClarificationResponseRequest response = new ClarificationResponseRequest();
        response.setClarificationId("99");
        response.setOptionCode("refund");

        IntentRoutingResult result = route(
                optionalSnapshot(List.of(optionalWorkflowMapping("refund"), optionalWorkflowMapping("exchange"))),
                "申请退款",
                response);

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.EXECUTE_WORKFLOW);
        assertThat(result.getExecutionQuery()).isEqualTo("订单商品有问题");
        assertThat(session.getStatus()).isEqualTo(IntentRoutingSessionStatus.RESOLVED);
    }

    @Test
    void shouldCollectRequiredSlotAndContinueOriginalTask() {
        IntentNodeDefinition refund = leaf("refund", "申请退款");
        refund.setSlots(List.of(IntentSlotDefinition.builder()
                .code("orderNo")
                .name("订单号")
                .valueType(IntentSlotValueType.STRING)
                .required(true)
                .build()));
        stubVersion(List.of(refund));
        IntentRoutingSessionEntity session = pendingSession(
                100L,
                IntentClarificationType.MISSING_SLOT,
                IntentClarificationContext.builder()
                        .originalQuery("我要退款")
                        .intentCode("refund")
                        .missingSlotCodes(List.of("orderNo"))
                        .collectedSlots(new LinkedHashMap<>())
                        .build());
        when(sessionRepo.findOwned(100L, 10L, 7L)).thenReturn(Optional.of(session));
        ClarificationResponseRequest response = new ClarificationResponseRequest();
        response.setClarificationId("100");
        response.setValue("A10086");

        IntentRoutingResult result = route(
                optionalSnapshot(List.of(optionalWorkflowMapping("refund"))),
                "A10086",
                response);

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.EXECUTE_WORKFLOW);
        assertThat(result.getExecutionQuery()).isEqualTo("我要退款\n已确认参数：orderNo=A10086");
        assertThat(session.getStatus()).isEqualTo(IntentRoutingSessionStatus.COMPLETED);
    }

    @Test
    void shouldFallThroughWhenOptionalFastRouteHasNoCandidate() {
        stubVersion(List.of(leaf("refund", "申请退款")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of());

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "聊聊天");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.NO_MATCH);
        assertThat(result.getExecutionQuery()).isEqualTo("聊聊天");
    }

    @Test
    void shouldFallThroughWhenTopOptionalLeafIsNotMapped() {
        stubVersion(List.of(leaf("refund", "申请退款"), leaf("exchange", "申请换货")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new IntentVectorRuntimeRepo.IntentCandidateRow("exchange-node", "exchange", 0.91)));

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "我要换货");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.NO_MATCH);
    }

    @Test
    void shouldDeferInsteadOfClarifyingUnsafeOptionalAmbiguity() {
        stubVersion(List.of(leaf("refund", "申请退款"), leaf("exchange", "申请换货")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of(
                new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.91),
                new IntentVectorRuntimeRepo.IntentCandidateRow("exchange-node", "exchange", 0.87)));

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "订单有问题");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getRoutingEvidence().getReason().name()).isEqualTo("UNMAPPED_CANDIDATE");
        assertThat(result.getRoutingEvidence().candidates())
                .extracting(candidate -> candidate.getIntentCode())
                .containsExactly("refund", "exchange");
        verify(sessionRepo, never()).insert(any());
    }

    @Test
    void shouldDeferAmbiguityAcrossDifferentCapabilities() {
        stubVersion(List.of(leaf("refund", "申请退款"), leaf("exchange", "申请换货")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of(
                new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.91),
                new IntentVectorRuntimeRepo.IntentCandidateRow("exchange-node", "exchange", 0.87)));
        ApplicationCapabilityDefinition exchangeCapability = ApplicationCapabilityDefinition.builder()
                .code("exchange_workflow")
                .type(ApplicationCapabilityType.WORKFLOW)
                .targetId(41L)
                .targetVersionId(51L)
                .build();
        AppRuntimeSnapshot snapshot = optionalSnapshot(
                List.of(
                        optionalWorkflowMapping("refund"),
                        workflowMapping("exchange", "exchange_workflow", 41L, 51L)),
                List.of(fallbackCap(), refundCapability(), exchangeCapability));

        IntentRoutingResult result = route(snapshot, "订单有问题");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getRoutingEvidence().getReason().name())
                .isEqualTo("AMBIGUOUS_ACROSS_CAPABILITIES");
        assertThat(result.getRoutingEvidence().candidates())
                .extracting(candidate -> candidate.getCapabilityCode())
                .containsExactly("refund_workflow", "exchange_workflow");
        verify(sessionRepo, never()).insert(any());
    }

    @Test
    void shouldRouteMappedOptionalLeafToLockedCapability() {
        stubVersion(List.of(leaf("refund", "申请退款")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.91)));

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "我要退款");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.EXECUTE_WORKFLOW);
        assertThat(result.getMapping().getCapabilityCode()).isEqualTo("refund_workflow");
        assertThat(result.getMapping().getTargetVersionId()).isEqualTo(50L);
    }

    private IntentRoutingResult route(AppRuntimeSnapshot snapshot, String query) {
        return route(snapshot, query, null);
    }

    private IntentRoutingResult route(
            AppRuntimeSnapshot snapshot,
            String query,
            ClarificationResponseRequest clarificationResponse) {
        LoginUser loginUser = new LoginUser(7L, "tester", "测试用户", null);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);
        ConversationContext context = context(snapshot, loginUser, turn, query, query);
        return service.route(context, clarificationResponse);
    }

    private static ConversationContext context(AppRuntimeSnapshot snapshot, LoginUser loginUser, ChatTurnBO turn, String originalQuery, String contextualizedQuery) {
        return ConversationContext.builder()
                .applicationSnapshot(snapshot)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery(originalQuery)
                .contextualizedQuery(contextualizedQuery)
                .recentMessages(List.of())
                .summary("")
                .longTermMemory("")
                .budget(contextBudget())
                .runtimeVariables(new RuntimeVariablesInitializer("Lingxi Workbench").initialize(loginUser, originalQuery, turn.conversationId()))
                .build();
    }

    private static ContextBudgetPolicy contextBudget() {
        return ContextBudgetPolicy.builder().maxHistoryRounds(10).maxSystemPromptTokens(4000).maxCapabilityTokens(4000)
                .maxHistoryTokens(4000).maxMemoryTokens(4000).maxInputTokens(4000).maxObservationTokens(4000)
                .reservedOutputTokens(4000).build();
    }

    private static IntentRoutingSessionEntity pendingSession(
            Long id,
            IntentClarificationType type,
            IntentClarificationContext context) {
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setId(id);
        session.setConversationId(10L);
        session.setUserId(7L);
        session.setApplicationVersionId(30L);
        session.setIntentTreeVersionId(20L);
        session.setStatus(IntentRoutingSessionStatus.PENDING);
        session.setClarificationType(type);
        session.setRoundNo(1);
        session.setContext(context);
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        return session;
    }

    private void stubVersion(List<IntentNodeDefinition> nodes) {
        RuntimeIntentTreeVersionEntity version = new RuntimeIntentTreeVersionEntity();
        version.setId(20L);
        version.setIntentTreeId(2L);
        version.setEmbeddingModelInstanceId(88L);
        version.setDefinitionSnapshot(new IntentTreeDefinition(
                1,
                88L,
                new IntentRoutingPolicy(5, new BigDecimal("0.65"), new BigDecimal("0.08")),
                nodes));
        when(intentTreeRepo.findVersion(2L, 20L)).thenReturn(Optional.of(version));
    }

    private static AppRuntimeSnapshot optionalSnapshot(List<ApplicationIntentMapping> mappings) {
        return optionalSnapshot(mappings, List.of(fallbackCap(), refundCapability()));
    }

    private static AppRuntimeSnapshot optionalSnapshot(
            List<ApplicationIntentMapping> mappings,
            List<ApplicationCapabilityDefinition> capabilities) {
        ApplicationIntentBinding fastRoutes = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(2L)
                .intentTreeVersionId(20L)
                .mappings(mappings)
                .build();
        return new AppRuntimeSnapshot(1L, "support", 30L, ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(capabilities)
                        .build())
                .optionalFastRoutes(fastRoutes)
                .build());
    }

    private static ApplicationCapabilityDefinition fallbackCap() {
        return ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(60L)
                .targetVersionId(70L)
                .build();
    }

    private static ApplicationCapabilityDefinition refundCapability() {
        return ApplicationCapabilityDefinition.builder()
                .code("refund_workflow")
                .type(ApplicationCapabilityType.WORKFLOW)
                .targetId(40L)
                .targetVersionId(50L)
                .build();
    }

    private static IntentNodeDefinition leaf(String code, String name) {
        return IntentNodeDefinition.builder()
                .id(code + "-node")
                .code(code)
                .name(name)
                .description(name)
                .nodeType(IntentNodeType.LEAF)
                .positiveExamples(List.of(name, "办理" + name))
                .enabled(true)
                .build();
    }

    private static ApplicationIntentMapping optionalWorkflowMapping(String intentCode) {
        return workflowMapping(intentCode, "refund_workflow", 40L, 50L);
    }

    private static ApplicationIntentMapping workflowMapping(
            String intentCode,
            String capabilityCode,
            Long targetId,
            Long targetVersionId) {
        return ApplicationIntentMapping.builder()
                .intentCode(intentCode)
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .capabilityCode(capabilityCode)
                .targetId(targetId)
                .targetVersionId(targetVersionId)
                .build();
    }
}
