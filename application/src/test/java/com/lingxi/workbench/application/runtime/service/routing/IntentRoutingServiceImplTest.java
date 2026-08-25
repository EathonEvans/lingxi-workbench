package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRecognizedSlot;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticSlotValue;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingDecisionEntity;
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
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
    private final IntentCandidateRetriever candidateRetriever = new IntentCandidateRetriever(
            intentTreeRepo, vectorRepo, modelConfigService, embeddingGateway);
    private final IntentSemanticDecisionService semanticDecisionService = mock(IntentSemanticDecisionService.class);
    private final IntentRoutingDecisionPersistenceServiceImpl decisionPersistenceService =
            mock(IntentRoutingDecisionPersistenceServiceImpl.class);
    private final IntentRoutingServiceImpl service = new IntentRoutingServiceImpl(
            intentTreeRepo, candidateRetriever, semanticDecisionService, sessionRepo, decisionPersistenceService);

    @BeforeEach
    void setUp() {
        when(decisionPersistenceService.append(any(), any())).thenReturn(900L);
        when(sessionRepo.findPending(10L, 7L, 30L)).thenReturn(Optional.empty());
        when(modelConfigService.getEmbeddingModelConfig(88L))
                .thenReturn(EmbeddingModelRuntimeConfig.builder().instanceId(88L).build());
        when(embeddingGateway.embed(any(), any())).thenReturn(List.of(new float[]{1F, 0F}));
        when(semanticDecisionService.decide(any(), any(), any())).thenAnswer(invocation -> {
            List<IntentCandidate> candidates = invocation.getArgument(2);
            IntentSemanticDecision decision = candidates.size() == 1
                    ? IntentSemanticDecision.builder()
                    .action(IntentSemanticAction.SELECT)
                    .selectedIntentCode(candidates.getFirst().intentCode())
                    .reasonCode(IntentDecisionReason.SINGLE_INTENT)
                    .build()
                    : IntentSemanticDecision.builder()
                    .action(IntentSemanticAction.CLARIFY)
                    .alternativeIntentCodes(candidates.stream().map(IntentCandidate::intentCode).toList())
                    .reasonCode(IntentDecisionReason.AMBIGUOUS_INTENT)
                    .build();
            return IntentSemanticDecisionResult.builder()
                    .decision(decision)
                    .tokenUsage(TokenUsage.empty())
                    .modelCalls(1)
                    .retryCount(0)
                    .modelInstanceId(9L)
                    .promptVersionId(10L)
                    .build();
        });
    }

    @Test
    void shouldReturnNoMatchWhenNoCandidatePassesThreshold() {
        stubVersion(List.of(leaf("refund", "申请退款")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of());

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "我想咨询");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getExecutionQuery()).isEqualTo("我想咨询");
        assertThat(result.getDecisionId()).isEqualTo(900L);
        ArgumentCaptor<IntentRoutingDecisionEntity> decisionCaptor =
                ArgumentCaptor.forClass(IntentRoutingDecisionEntity.class);
        verify(decisionPersistenceService).append(decisionCaptor.capture(), isNull());
        assertThat(decisionCaptor.getValue().getAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(decisionCaptor.getValue().getReason().getCode()).isEqualTo("NO_CANDIDATE");
        assertThat(decisionCaptor.getValue().getCandidateEvidence()).isEmpty();
        verify(semanticDecisionService, never()).decide(any(), any(), any());
    }

    @Test
    void shouldRouteHighSimilarityLeafToPublishedWorkflow() {
        stubVersion(List.of(leaf("refund", "申请退款")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.91)));

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "我要退款");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.EXECUTE_WORKFLOW);
        assertThat(result.getIntentCode()).isEqualTo("refund");
        assertThat(result.getExecutionQuery()).isEqualTo("我要退款");
        assertThat(result.getDecisionId()).isEqualTo(900L);
        assertThat(result.getModelCalls()).isEqualTo(1);
        // 路由决策证据携带门控原因与命中相似度，供 INTENT_ROUTE 观测
        assertThat(result.getRoutingEvidence().getReason().name()).isEqualTo("ROUTED_BY_SEMANTIC");
        assertThat(result.getRoutingEvidence().candidates())
                .extracting(candidate -> candidate.getIntentCode() + ":" + candidate.getSimilarity())
                .containsExactly("refund:0.91");
        ArgumentCaptor<IntentRoutingDecisionEntity> decisionCaptor =
                ArgumentCaptor.forClass(IntentRoutingDecisionEntity.class);
        verify(decisionPersistenceService).append(decisionCaptor.capture(), isNull());
        IntentRoutingDecisionEntity decision = decisionCaptor.getValue();
        assertThat(decision.getConversationId()).isEqualTo(10L);
        assertThat(decision.getUserMessageId()).isEqualTo(11L);
        assertThat(decision.getAssistantMessageId()).isEqualTo(12L);
        assertThat(decision.getApplicationVersionId()).isEqualTo(30L);
        assertThat(decision.getIntentTreeVersionId()).isEqualTo(20L);
        assertThat(decision.getSelectedIntentCode()).isEqualTo("refund");
        assertThat(decision.getCapabilityCode()).isEqualTo("refund_workflow");
        assertThat(decision.getCandidateEvidence()).singleElement().satisfies(candidate -> {
            assertThat(candidate.intentCode()).isEqualTo("refund");
            assertThat(candidate.rank()).isEqualTo(1);
            assertThat(candidate.capabilityCode()).isEqualTo("refund_workflow");
        });
        assertThat(decision.getSemanticDecision().recognizedSlotCodes()).isEmpty();
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
    void shouldUseRecognizedRequiredSlotWithoutClarification() {
        IntentNodeDefinition refund = leaf("refund", "申请退款");
        refund.setSlots(List.of(IntentSlotDefinition.builder()
                .code("orderNo")
                .name("订单号")
                .valueType(IntentSlotValueType.STRING)
                .required(true)
                .build()));
        stubVersion(List.of(refund));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.91)));
        IntentSemanticSlotValue slotValue = new IntentSemanticSlotValue();
        slotValue.setStringValue("A10086");
        IntentRecognizedSlot recognizedSlot = new IntentRecognizedSlot();
        recognizedSlot.setSlotCode("orderNo");
        recognizedSlot.setValue(slotValue);
        IntentSemanticDecisionResult semanticResult = IntentSemanticDecisionResult.builder()
                .decision(IntentSemanticDecision.builder()
                        .action(IntentSemanticAction.SELECT)
                        .selectedIntentCode("refund")
                        .recognizedSlots(List.of(recognizedSlot))
                        .reasonCode(IntentDecisionReason.SINGLE_INTENT)
                        .build())
                .tokenUsage(TokenUsage.empty())
                .modelCalls(1)
                .retryCount(0)
                .modelInstanceId(9L)
                .promptVersionId(10L)
                .build();
        doReturn(semanticResult).when(semanticDecisionService).decide(any(), any(), any());

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))),
                "退款订单A10086");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.EXECUTE_WORKFLOW);
        assertThat(result.getExecutionQuery()).isEqualTo("退款订单A10086\n已确认参数：orderNo=A10086");
        verify(sessionRepo, never()).insert(any());
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
        // 同能力歧义的证据同样进入观测：门控原因 + 全部候选相似度
        assertThat(result.getRoutingEvidence().getReason().name()).isEqualTo("SEMANTIC_CLARIFICATION");
        assertThat(result.getRoutingEvidence().candidates())
                .extracting(candidate -> candidate.getIntentCode() + ":" + candidate.getSimilarity())
                .containsExactly("refund:0.91", "exchange:0.87");
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
        assertThat(result.getRoutingEvidence().getReason().getCode()).isEqualTo("CLARIFICATION_RESOLVED");
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
        assertThat(result.getRoutingEvidence().getReason().getCode()).isEqualTo("CLARIFICATION_RESOLVED");
        assertThat(session.getStatus()).isEqualTo(IntentRoutingSessionStatus.COMPLETED);
    }

    @Test
    void shouldRejectInvalidSlotValueAndReaskWithFormatHint() {
        IntentNodeDefinition refund = leaf("refund", "申请退款");
        refund.setSlots(List.of(IntentSlotDefinition.builder()
                .code("orderNo")
                .name("订单号")
                .valueType(IntentSlotValueType.NUMBER)
                .required(true)
                .build()));
        stubVersion(List.of(refund));
        IntentRoutingSessionEntity session = pendingSession(
                101L,
                IntentClarificationType.MISSING_SLOT,
                IntentClarificationContext.builder()
                        .originalQuery("我要退款")
                        .intentCode("refund")
                        .missingSlotCodes(List.of("orderNo"))
                        .collectedSlots(new LinkedHashMap<>())
                        .build());
        when(sessionRepo.findOwned(101L, 10L, 7L)).thenReturn(Optional.of(session));
        ClarificationResponseRequest response = new ClarificationResponseRequest();
        response.setClarificationId("101");
        response.setValue("A10086");

        IntentRoutingResult result = route(
                optionalSnapshot(List.of(optionalWorkflowMapping("refund"))),
                "A10086",
                response);

        // 非法值不写入槽位，重问并提示数字格式
        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.CLARIFICATION);
        assertThat(result.getClarification().getQuestion()).contains("数字");
        assertThat(result.getRoutingEvidence().getReason().getCode()).isEqualTo("CLARIFICATION_RETRY");
        assertThat(session.getRoundNo()).isEqualTo(2);
        assertThat(session.getContext().getCollectedSlots()).doesNotContainKey("orderNo");
        assertThat(session.getStatus()).isEqualTo(IntentRoutingSessionStatus.PENDING);
    }

    @Test
    void shouldAcceptValidDateAndBooleanSlotValues() {
        IntentNodeDefinition refund = leaf("refund", "申请退款");
        refund.setSlots(List.of(
                IntentSlotDefinition.builder()
                        .code("orderDate")
                        .name("下单日期")
                        .valueType(IntentSlotValueType.DATE)
                        .required(true)
                        .build(),
                IntentSlotDefinition.builder()
                        .code("urgent")
                        .name("是否加急")
                        .valueType(IntentSlotValueType.BOOLEAN)
                        .required(true)
                        .build()));
        stubVersion(List.of(refund));
        IntentRoutingSessionEntity session = pendingSession(
                102L,
                IntentClarificationType.MISSING_SLOT,
                IntentClarificationContext.builder()
                        .originalQuery("我要退款")
                        .intentCode("refund")
                        .missingSlotCodes(List.of("orderDate", "urgent"))
                        .collectedSlots(new LinkedHashMap<>())
                        .build());
        when(sessionRepo.findOwned(102L, 10L, 7L)).thenReturn(Optional.of(session));
        ClarificationResponseRequest response = new ClarificationResponseRequest();
        response.setClarificationId("102");
        response.setValue("2026/1/5");

        IntentRoutingResult result = route(
                optionalSnapshot(List.of(optionalWorkflowMapping("refund"))),
                "2026/1/5",
                response);

        // DATE 合法 → 继续询问下一个槽位（BOOLEAN），且日期已入收集集合
        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.CLARIFICATION);
        assertThat(session.getContext().getCollectedSlots()).containsEntry("orderDate", "2026/1/5");
        assertThat(session.getContext().getMissingSlotCodes()).containsExactly("urgent");
    }

    @Test
    void shouldFailSlotCollectionAfterMaxInvalidAttempts() {
        IntentNodeDefinition refund = leaf("refund", "申请退款");
        refund.setSlots(List.of(IntentSlotDefinition.builder()
                .code("orderNo")
                .name("订单号")
                .valueType(IntentSlotValueType.NUMBER)
                .required(true)
                .build()));
        stubVersion(List.of(refund));
        IntentRoutingSessionEntity session = pendingSession(
                103L,
                IntentClarificationType.MISSING_SLOT,
                IntentClarificationContext.builder()
                        .originalQuery("我要退款")
                        .intentCode("refund")
                        .missingSlotCodes(List.of("orderNo"))
                        .collectedSlots(new LinkedHashMap<>())
                        .build());
        when(sessionRepo.findOwned(103L, 10L, 7L)).thenReturn(Optional.of(session));
        ClarificationResponseRequest response = new ClarificationResponseRequest();
        response.setClarificationId("103");
        response.setValue("abc");
        // 已是第 2 轮（达到上限），再次格式错误直接失败兜底
        session.setRoundNo(2);

        IntentRoutingResult result = route(
                optionalSnapshot(List.of(optionalWorkflowMapping("refund"))),
                "abc",
                response);

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(session.getStatus()).isEqualTo(IntentRoutingSessionStatus.FAILED);
    }

    @Test
    void shouldFallThroughWhenOptionalIntentRoutingHasNoCandidate() {
        stubVersion(List.of(leaf("refund", "申请退款")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class))).thenReturn(List.of());

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "聊聊天");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getExecutionQuery()).isEqualTo("聊聊天");
    }

    @Test
    void shouldFallThroughWhenTopOptionalLeafIsNotMapped() {
        stubVersion(List.of(leaf("refund", "申请退款"), leaf("exchange", "申请换货")));
        when(vectorRepo.search(any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(new IntentVectorRuntimeRepo.IntentCandidateRow("exchange-node", "exchange", 0.91)));

        IntentRoutingResult result = route(optionalSnapshot(List.of(optionalWorkflowMapping("refund"))), "我要换货");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getRoutingEvidence().getReason().name()).isEqualTo("UNMAPPED_CANDIDATE");
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
        IntentSemanticDecisionResult decisionResult = IntentSemanticDecisionResult.builder()
                        .decision(IntentSemanticDecision.builder()
                                .action(IntentSemanticAction.DEFER)
                                .reasonCode(IntentDecisionReason.MULTIPLE_INTENTS)
                                .build())
                        .tokenUsage(TokenUsage.empty())
                        .modelCalls(1)
                        .modelInstanceId(9L)
                        .promptVersionId(10L)
                        .build();
        doReturn(decisionResult).when(semanticDecisionService).decide(any(), any(), any());

        IntentRoutingResult result = route(snapshot, "订单有问题");

        assertThat(result.getAction()).isEqualTo(IntentRoutingAction.DEFER_TO_AGENT);
        assertThat(result.getRoutingEvidence().getReason().name())
                .isEqualTo("SEMANTIC_DEFER");
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

    private static ConversationContext context(AppRuntimeSnapshot snapshot, LoginUser loginUser, ChatTurnBO turn, String originalQuery, String rewrittenQuery) {
        return ConversationContext.builder()
                .applicationSnapshot(snapshot)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery(originalQuery)
                .rewrittenQuery(rewrittenQuery)
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
                2,
                88L,
                IntentRoutingPolicy.builder()
                        .topK(5)
                        .recallThreshold(new BigDecimal("0.65"))
                        .chatModelInstanceId(89L)
                        .decisionPromptVersionId(90L)
                        .maxOutputTokens(512)
                        .timeoutSeconds(30)
                        .maxRepeatAttempts(1)
                        .decisionFailureAction(IntentDecisionFailureAction.DEFER)
                        .build(),
                nodes));
        when(intentTreeRepo.findVersion(2L, 20L)).thenReturn(Optional.of(version));
    }

    private static AppRuntimeSnapshot optionalSnapshot(List<ApplicationIntentMapping> mappings) {
        return optionalSnapshot(mappings, List.of(fallbackCap(), refundCapability()));
    }

    private static AppRuntimeSnapshot optionalSnapshot(
            List<ApplicationIntentMapping> mappings,
            List<ApplicationCapabilityDefinition> capabilities) {
        ApplicationIntentBinding intentRouting = ApplicationIntentBinding.builder()
                .enabled(true)
                .intentTreeId(2L)
                .intentTreeVersionId(20L)
                .mappings(mappings)
                .build();
        return new AppRuntimeSnapshot(1L, "support", 30L, ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(new AgentCollaborationDefinition(
                        new ApplicationAgentReference(60L, 70L, "默认助手", "负责收口"),
                        List.of(), List.of(), List.of()))
                .memoryConf(MemoryConfig.disabled())
                .contextPolicy(ApplicationContextPolicy.defaults())
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(capabilities)
                        .build())
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .intentRouting(intentRouting)
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
