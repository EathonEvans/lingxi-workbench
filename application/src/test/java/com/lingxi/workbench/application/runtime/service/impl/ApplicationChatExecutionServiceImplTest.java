package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.model.ApplicationChatExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerContextResolution;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.CapabilityExecutionService;
import com.lingxi.workbench.application.runtime.service.ChatExecutionContextRegistry;
import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.IntentRoutingService;
import com.lingxi.workbench.application.runtime.service.PlannerRuntimeCoordinator;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.ConversationContextService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.exception.WorkflowExecutionException;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import org.junit.jupiter.api.Test;
import io.micrometer.observation.ObservationRegistry;
import com.lingxi.workbench.observability.service.AiStageObserver;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationChatExecutionServiceImplTest {

    @Test
    void shouldReturnFixedResponseWithoutStartingExecution() {
        ApplicationChatExecutionServiceImpl service = service(
                unexpectedCapabilityService(), new ChatExecutionContextRegistry(), conversationContextService(),
                (context, response) -> IntentRoutingResult.builder()
                        .action(IntentRoutingAction.FIXED_RESPONSE)
                        .response("固定回答")
                        .build(),
                plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(fastRouteSnapshot(), "固定问题"));

        assertEquals("固定回答", result.answer());
        assertEquals(TokenUsage.empty(), result.tokenUsage());
    }

    @Test
    void shouldPublishClarificationWithoutStartingExecution() {
        ChatClarificationEventBO clarification = ChatClarificationEventBO.builder()
                .clarificationId("clarification-1")
                .question("请选择需要办理的业务")
                .options(List.of(new ChatClarificationEventBO.ClarificationOptionBO("refund", "申请退款")))
                .round(1)
                .build();
        ChatExecutionEventPublisher eventPublisher = mock(ChatExecutionEventPublisher.class);
        ApplicationChatExecutionServiceImpl service = service(
                unexpectedCapabilityService(), new ChatExecutionContextRegistry(), conversationContextService(),
                (context, response) -> IntentRoutingResult.builder()
                        .action(IntentRoutingAction.CLARIFICATION)
                        .response("请选择需要办理的业务")
                        .clarification(clarification)
                        .build(),
                plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(fastRouteSnapshot(), "办理业务", eventPublisher));

        assertEquals("请选择需要办理的业务", result.answer());
        verify(eventPublisher).publishClarification(clarification);
    }

    @Test
    void shouldExecuteWorkflowFastRouteThroughPublishedCapability() {
        ApplicationIntentMapping mapping = mapping(
                IntentExecutionTargetType.WORKFLOW, "refund_workflow", 40L, 50L);
        ApplicationCapabilityDefinition capability = capability(
                "refund_workflow", ApplicationCapabilityType.WORKFLOW, 40L, 50L);
        MemoryConfig memory = MemoryConfig.builder()
                .interactionRecord(InteractionRecordMemoryConfig.builder()
                        .enabled(true)
                        .embeddingModelInstanceId(70L)
                        .build())
                .build();
        AppRuntimeSnapshot snapshot = routedSnapshot(mapping, capability, memory, false);
        ChatExecutionContextRegistry registry = new ChatExecutionContextRegistry();
        AtomicReference<String> executionId = new AtomicReference<>();
        CapabilityExecutionService capabilityService = request -> {
            executionId.set(request.context().getExecutionId());
            assertEquals("refund_workflow", request.capability().getCode());
            assertEquals("退款问题", request.inputs().getString("query"));
            assertEquals(70L, request.context().getMemoryConfig().interactionRecord().getEmbeddingModelInstanceId());
            assertEquals("测试用户", registry.require(request.context().getExecutionId())
                    .getRuntimeVariables().require(RuntimeVariableAccessors.USER_NAME));
            request.context().recordUsage(TokenUsage.of(5, 1, 6));
            return new CapabilityExecutionResult("退款回答", 99L, List.of());
        };
        ApplicationChatExecutionServiceImpl service = service(
                capabilityService, registry, conversationContextService(TokenUsage.of(10, 2, 12)),
                routed(mapping), plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(snapshot, "退款问题"));

        assertEquals("退款回答", result.answer());
        assertEquals(99L, result.modelInstanceId());
        assertEquals(TokenUsage.of(15, 3, 18), result.tokenUsage());
        assertThrows(WorkflowExecutionException.class, () -> registry.require(executionId.get()));
    }

    @Test
    void shouldExecuteAgentFastRouteThroughPublishedCapability() {
        ApplicationIntentMapping mapping = mapping(
                IntentExecutionTargetType.AGENT, "consult_agent", 60L, 70L);
        ApplicationCapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.REACT_AGENT, 60L, 70L);
        AtomicReference<CapabilityExecutionRequest> executed = new AtomicReference<>();
        ApplicationChatExecutionServiceImpl service = service(
                request -> {
                    executed.set(request);
                    return new CapabilityExecutionResult("Agent回答", 88L, List.of());
                },
                new ChatExecutionContextRegistry(), conversationContextService(), routed(mapping), plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(
                routedSnapshot(mapping, capability, MemoryConfig.disabled(), false), "咨询问题"));

        assertEquals("Agent回答", result.answer());
        assertEquals("consult_agent", executed.get().capability().getCode());
        assertEquals("咨询问题", executed.get().inputs().getString("query"));
    }

    @Test
    void shouldExecuteReservedDefaultAgentCapabilityWhenFastRouteIsDisabled() {
        AtomicReference<CapabilityExecutionRequest> executed = new AtomicReference<>();
        IntentRoutingService routingService = (context, response) -> {
            throw new AssertionError("未启用快速路由时不应调用路由服务");
        };
        ApplicationChatExecutionServiceImpl service = service(
                request -> {
                    executed.set(request);
                    return new CapabilityExecutionResult("默认回答", 77L, List.of());
                },
                new ChatExecutionContextRegistry(), conversationContextService(), routingService, plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(defaultSnapshot(), "普通问题"));

        assertEquals("默认回答", result.answer());
        assertEquals("default_agent", executed.get().capability().getCode());
        assertEquals("普通问题", executed.get().inputs().getString("query"));
    }

    @Test
    void shouldUsePlannerWhenFastRouteDoesNotMatch() {
        ChatExecutionContextRegistry registry = new ChatExecutionContextRegistry();
        PlannerRuntimeCoordinator coordinator = new PlannerRuntimeCoordinator() {
            @Override
            public PlannerContextResolution resolveInteraction(
                    ConversationContext context,
                    com.lingxi.workbench.chat.model.request.ClarificationResponseRequest response) {
                return new PlannerContextResolution(context, false, Set.of());
            }

            @Override
            public String startRun(ConversationContext context) {
                return "run-1";
            }

            @Override
            public PlanExecutionResult execute(PlannerRuntimeRequest request) {
                assertEquals("run-1", request.getRunId());
                assertEquals("组合问题", request.getConversationContext().routingQuery());
                assertEquals("run-1", request.getExecutionContext().getExecutionId());
                return new PlanExecutionResult("规划回答", 81L, List.of());
            }
        };
        ApplicationChatExecutionServiceImpl service = service(
                request -> {
                    throw new AssertionError("Planner路径不应由入口直接执行能力");
                },
                registry, conversationContextService(),
                (context, response) -> IntentRoutingResult.noMatch(context.routingQuery()), coordinator);

        ApplicationIntentMapping mapping = mapping(
                IntentExecutionTargetType.AGENT, "consult_agent", 61L, 71L);
        ApplicationCapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.REACT_AGENT, 61L, 71L);
        AppRuntimeSnapshot snapshot = routedSnapshot(
                mapping, capability, MemoryConfig.disabled(), true);

        ApplicationChatResult result = service.execute(executionRequest(snapshot, "组合问题"));

        assertEquals("规划回答", result.answer());
        assertEquals(81L, result.modelInstanceId());
        assertThrows(WorkflowExecutionException.class, () -> registry.require("run-1"));
    }

    @Test
    void shouldRejectFastRouteWithoutCapabilityCode() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, null, 60L, 70L);

        assertThrows(IllegalArgumentException.class, () -> routedSnapshot(
                mapping, capability("consult_agent", ApplicationCapabilityType.REACT_AGENT, 60L, 70L),
                MemoryConfig.disabled(), false));
    }

    @Test
    void shouldRejectCapabilityMissingFromPublishedCatalog() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, "missing_agent", 60L, 70L);

        assertThrows(IllegalArgumentException.class, () -> routedSnapshot(
                mapping, capability("other_agent", ApplicationCapabilityType.REACT_AGENT, 60L, 70L),
                MemoryConfig.disabled(), false));
    }

    @Test
    void shouldRejectCapabilityTypeInconsistentWithRouteAction() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, "consult_agent", 60L, 70L);
        ApplicationCapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.WORKFLOW, 60L, 70L);

        assertThrows(IllegalArgumentException.class, () -> routedSnapshot(
                mapping, capability, MemoryConfig.disabled(), false));
    }

    @Test
    void shouldRejectRouteTargetInconsistentWithPublishedCapability() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, "consult_agent", 60L, 70L);
        ApplicationCapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.REACT_AGENT, 61L, 71L);

        assertThrows(IllegalArgumentException.class, () -> routedSnapshot(
                mapping, capability, MemoryConfig.disabled(), false));
    }

    @Test
    void shouldRejectIncompleteExecutionRequest() {
        ApplicationChatExecutionServiceImpl service = service(
                unexpectedCapabilityService(), new ChatExecutionContextRegistry(), conversationContextService(),
                (context, response) -> IntentRoutingResult.skipped(context.routingQuery()), plannerDisabled());
        ApplicationChatExecutionRequest request = ApplicationChatExecutionRequest.builder()
                .application(defaultSnapshot())
                .query("问题")
                .build();

        assertThrows(BusinessException.class, () -> service.execute(null));
        assertThrows(BusinessException.class, () -> service.execute(request));
    }

    private static ApplicationChatExecutionServiceImpl service(CapabilityExecutionService capabilityService,
                                                               ChatExecutionContextRegistry registry,
                                                               ConversationContextService contextService,
                                                               IntentRoutingService routingService,
                                                               PlannerRuntimeCoordinator coordinator) {
        return new ApplicationChatExecutionServiceImpl(
                capabilityService, registry, contextService, routingService, coordinator,
                new AiStageObserver(ObservationRegistry.NOOP));
    }

    private static CapabilityExecutionService unexpectedCapabilityService() {
        return request -> {
            throw new AssertionError("非法发布快照不应进入能力执行服务");
        };
    }

    private static IntentRoutingService routed(ApplicationIntentMapping mapping) {
        IntentRoutingAction action = mapping.getTargetType() == IntentExecutionTargetType.WORKFLOW
                ? IntentRoutingAction.EXECUTE_WORKFLOW
                : IntentRoutingAction.EXECUTE_AGENT;
        return (context, response) -> IntentRoutingResult.builder()
                .action(action)
                .intentCode(mapping.getIntentCode())
                .mapping(mapping)
                .executionQuery(context.routingQuery())
                .build();
    }

    private static ApplicationChatExecutionRequest executionRequest(AppRuntimeSnapshot snapshot, String query) {
        return executionRequest(snapshot, query, ChatExecutionEventPublisher.noop());
    }

    private static ApplicationChatExecutionRequest executionRequest(AppRuntimeSnapshot snapshot, String query,
                                                                    ChatExecutionEventPublisher eventPublisher) {
        return ApplicationChatExecutionRequest.builder()
                .application(snapshot)
                .query(query)
                .loginUser(new LoginUser(7L, "tester", "测试用户", null))
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .eventPublisher(eventPublisher)
                .build();
    }

    private static PlannerRuntimeCoordinator plannerDisabled() {
        return new PlannerRuntimeCoordinator() {
            @Override
            public PlannerContextResolution resolveInteraction(
                    ConversationContext context,
                    com.lingxi.workbench.chat.model.request.ClarificationResponseRequest response) {
                return new PlannerContextResolution(context, false, Set.of());
            }

            @Override
            public String startRun(ConversationContext context) {
                throw new AssertionError("未启用Planner时不应创建运行");
            }

            @Override
            public PlanExecutionResult execute(PlannerRuntimeRequest request) {
                throw new AssertionError("未启用Planner时不应执行计划");
            }
        };
    }

    private static ConversationContextService conversationContextService() {
        return conversationContextService(TokenUsage.empty());
    }

    private static ConversationContextService conversationContextService(TokenUsage preparationUsage) {
        RuntimeVariablesInitializer initializer = new RuntimeVariablesInitializer("Lingxi Workbench");
        return (application, query, loginUser, turn) -> ConversationContext.builder()
                .applicationSnapshot(application)
                .loginUser(loginUser)
                .turn(turn)
                .originalQuery(query)
                .contextualizedQuery(query)
                .recentMessages(List.of())
                .summary("")
                .longTermMemory("")
                .budget(contextBudget())
                .runtimeVariables(runtimeVariables(initializer, loginUser, query, turn))
                .preparationTokenUsage(preparationUsage)
                .build();
    }

    private static ContextBudgetPolicy contextBudget() {
        return ContextBudgetPolicy.builder().maxHistoryRounds(10).maxSystemPromptTokens(4000).maxCapabilityTokens(4000)
                .maxHistoryTokens(4000).maxMemoryTokens(4000).maxInputTokens(4000).maxObservationTokens(4000)
                .reservedOutputTokens(4000).build();
    }

    private static RuntimeVariables runtimeVariables(RuntimeVariablesInitializer initializer, LoginUser loginUser,
                                                     String query, ChatTurnBO turn) {
        RuntimeVariables variables = initializer.initialize(loginUser, query, turn.conversationId());
        variables.put(RuntimeVariableAccessors.SESSION_HISTORY, List.of());
        return variables;
    }

    private static AppRuntimeSnapshot defaultSnapshot() {
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(defaultAgent())
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(defaultCapability()))
                        .build())
                .plannerPolicy(PlannerPolicy.disabled())
                .build();
        return new AppRuntimeSnapshot(1L, "support", 30L, definition);
    }

    private static AppRuntimeSnapshot fastRouteSnapshot() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, "consult_agent", 61L, 71L);
        ApplicationCapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.REACT_AGENT, 61L, 71L);
        return routedSnapshot(mapping, capability, MemoryConfig.disabled(), false);
    }

    private static AppRuntimeSnapshot routedSnapshot(ApplicationIntentMapping mapping,
                                                     ApplicationCapabilityDefinition mappedCapability,
                                                     MemoryConfig memory, boolean plannerEnabled) {
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(defaultAgent())
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(defaultCapability(), mappedCapability))
                        .build())
                .optionalFastRoutes(ApplicationIntentBinding.builder()
                        .enabled(true)
                        .intentTreeId(2L)
                        .intentTreeVersionId(20L)
                        .mappings(List.of(mapping))
                        .build())
                .memoryConf(memory)
                .plannerPolicy(plannerEnabled ? plannerPolicy() : PlannerPolicy.disabled())
                .build();
        return new AppRuntimeSnapshot(1L, "support", 30L, definition);
    }

    private static ApplicationAgentReference defaultAgent() {
        return new ApplicationAgentReference(60L, 70L, "默认助手", "处理常规请求");
    }

    private static ApplicationCapabilityDefinition defaultCapability() {
        return capability("default_agent", ApplicationCapabilityType.REACT_AGENT, 60L, 70L);
    }

    private static ApplicationCapabilityDefinition capability(String code, ApplicationCapabilityType type,
                                                              Long targetId, Long targetVersionId) {
        return ApplicationCapabilityDefinition.builder()
                .code(code)
                .type(type)
                .targetId(targetId)
                .targetVersionId(targetVersionId)
                .build();
    }

    private static ApplicationIntentMapping mapping(IntentExecutionTargetType type, String capabilityCode,
                                                    Long targetId, Long targetVersionId) {
        return ApplicationIntentMapping.builder()
                .intentCode("intent")
                .targetType(type)
                .capabilityCode(capabilityCode)
                .targetId(targetId)
                .targetVersionId(targetVersionId)
                .build();
    }

    private static PlannerPolicy plannerPolicy() {
        return PlannerPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(80L)
                .promptVersionId(90L)
                .maxSteps(6)
                .maxReplans(1)
                .maxModelCalls(6)
                .maxToolCalls(6)
                .tokenBudget(12000)
                .timeoutSeconds(60)
                .build();
    }
}
