package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerContextResolution;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeWorkflowVersionEntity;
import com.lingxi.workbench.application.runtime.repo.WorkflowRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.AgentChatService;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.service.AgentRuntimeService;
import com.lingxi.workbench.application.runtime.service.ChatExecutionContextRegistry;
import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.PlannerRuntimeCoordinator;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ContextBudget;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.ConversationContextService;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.exception.WorkflowExecutionException;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import com.lingxi.workbench.workflow.model.WorkflowDefinition;
import com.lingxi.workbench.workflow.model.WorkflowExecutionResult;
import com.lingxi.workbench.workflow.model.WorkflowNodeDefinition;
import com.lingxi.workbench.workflow.model.WorkflowNodeType;
import com.lingxi.workbench.workflow.model.config.AgentNodeConfig;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.service.WorkflowExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationChatExecutionServiceImplTest {

    @Test
    void shouldExecutePublishedSnapshotAndReturnResponse() {
        AtomicReference<String> executionId = new AtomicReference<>();
        ChatExecutionContextRegistry registry =
                new ChatExecutionContextRegistry();
        WorkflowExecutor executor = (definition, request) -> {
            executionId.set(request.executionId());
            assertEquals("问题", request.inputs().get("query"));
            assertEquals("30", request.applicationVersionId());
            assertEquals(
                    "测试用户",
                    registry.require(request.executionId())
                            .getRuntimeVariables()
                            .require(RuntimeVariableAccessors.USER_NAME));
            return new WorkflowExecutionResult(
                    request.executionId(), Map.of("response", "工作流回答"), List.of());
        };
        ApplicationChatExecutionServiceImpl service =
                new ApplicationChatExecutionServiceImpl(
                        executor,
                        agentChatService(),
                        agentRuntimeService(),
                        registry,
                        conversationContextService(),
                (context, response) -> IntentRoutingResult.workflow(null, context.routingQuery()),
                workflowRepo(null),
                plannerCoordinator());

        ApplicationChatResult result = service.execute(
                snapshot(),
                "问题",
                new LoginUser(7L, "tester", "测试用户", null),
                new ChatTurnBO(10L, 11L, 12L, 3),
                null,
                ChatExecutionEventPublisher.noop());

        assertEquals("工作流回答", result.answer());
        assertThrows(
                WorkflowExecutionException.class,
                () -> registry.require(executionId.get()));
    }

    @Test
    void shouldExecuteWorkflowVersionLockedByApplicationAndPropagateMemoryConfig() {
        WorkflowDefinition lockedWorkflow = new WorkflowDefinition(1, "locked-refund", List.of(), List.of());
        ApplicationIntentMapping mapping = ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .targetId(40L)
                .targetVersionId(50L)
                .build();
        MemoryConfig memory = MemoryConfig.builder()
                .interactionRecord(InteractionRecordMemoryConfig.builder()
                        .enabled(true)
                        .embeddingModelInstanceId(70L)
                        .build())
                .build();
        ApplicationRuntimeSnapshot snapshot = new ApplicationRuntimeSnapshot(
                1L,
                "support",
                30L,
                ApplicationDefinition.builder()
                        .schemaVersion(2)
                        .intentBinding(ApplicationIntentBinding.builder().enabled(true).build())
                        .memory(memory)
                        .build());
        ChatExecutionContextRegistry registry = new ChatExecutionContextRegistry();
        AtomicReference<String> workflowCode = new AtomicReference<>();
        WorkflowExecutor executor = (definition, request) -> {
            workflowCode.set(definition.code());
            assertEquals("50", request.applicationVersionId());
            assertEquals(70L, registry.require(request.executionId()).getMemoryConfig()
                    .interactionRecord().getEmbeddingModelInstanceId());
            return new WorkflowExecutionResult(request.executionId(), Map.of("response", "退款回答"), List.of());
        };
        ApplicationChatExecutionServiceImpl service = new ApplicationChatExecutionServiceImpl(
                executor,
                agentChatService(),
                agentRuntimeService(),
                registry,
                conversationContextService(),
                (context, response) -> IntentRoutingResult.builder()
                        .action(IntentRoutingAction.EXECUTE_WORKFLOW)
                        .intentCode("refund")
                        .mapping(mapping)
                        .executionQuery(context.routingQuery())
                        .build(),
                workflowRepo(lockedWorkflow),
                plannerCoordinator());

        ApplicationChatResult result = service.execute(
                snapshot,
                "退款问题",
                new LoginUser(7L, "tester", "测试用户", null),
                new ChatTurnBO(10L, 11L, 12L, 3),
                null,
                ChatExecutionEventPublisher.noop());

        assertEquals("退款回答", result.answer());
        assertEquals("locked-refund", workflowCode.get());
    }

    @Test
    void shouldExecuteLockedAgentVersionWithoutWorkflowExecutor() {
        ApplicationIntentMapping mapping = ApplicationIntentMapping.builder()
                .intentCode("consult")
                .targetType(IntentExecutionTargetType.AGENT)
                .targetId(60L)
                .targetVersionId(70L)
                .build();
        ApplicationRuntimeSnapshot snapshot = new ApplicationRuntimeSnapshot(
                1L,
                "support",
                30L,
                ApplicationDefinition.builder()
                        .schemaVersion(2)
                        .intentBinding(ApplicationIntentBinding.builder().enabled(true).build())
                        .build());
        AtomicReference<Long> executedAgentVersionId = new AtomicReference<>();
        AgentChatService directAgent = (agentId, versionId, query, context) -> {
            assertEquals(60L, agentId);
            assertEquals("咨询问题", query);
            assertEquals("咨询问题", context.getConversationContext().getOriginalQuery());
            executedAgentVersionId.set(versionId);
            context.recordUsage(TokenUsage.of(5, 1, 6));
            return "Agent直接回答";
        };
        ApplicationChatExecutionServiceImpl service = new ApplicationChatExecutionServiceImpl(
                (definition, request) -> {
                    throw new AssertionError("AGENT意图目标不应进入WorkflowExecutor");
                },
                directAgent,
                agentRuntimeService(),
                new ChatExecutionContextRegistry(),
                conversationContextService(TokenUsage.of(10, 2, 12)),
                (context, response) -> IntentRoutingResult.builder()
                        .action(IntentRoutingAction.EXECUTE_AGENT)
                        .intentCode("consult")
                        .mapping(mapping)
                        .executionQuery(context.routingQuery())
                        .build(),
                workflowRepo(null),
                plannerCoordinator());

        ApplicationChatResult result = service.execute(
                snapshot,
                "咨询问题",
                new LoginUser(7L, "tester", "测试用户", null),
                new ChatTurnBO(10L, 11L, 12L, 3),
                null,
                ChatExecutionEventPublisher.noop());

        assertEquals("Agent直接回答", result.answer());
        assertEquals(70L, executedAgentVersionId.get());
        assertEquals(List.of(), result.traces());
        assertEquals(99L, result.modelInstanceId());
        assertEquals(TokenUsage.of(15, 3, 18), result.tokenUsage());
    }

    @Test
    void shouldExecuteDefaultReactAgentWithoutIntentRouting() {
        ApplicationAgentReference defaultAgent = new ApplicationAgentReference(60L, 70L, "默认助手", "处理常规请求");
        ApplicationCapabilityDefinition capability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(60L)
                .targetVersionId(70L)
                .build();
        ApplicationRuntimeSnapshot snapshot = new ApplicationRuntimeSnapshot(
                1L,
                "support",
                30L,
                ApplicationDefinition.builder()
                        .schemaVersion(4)
                        .defaultAgent(defaultAgent)
                        .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(capability)).build())
                        .build());
        AtomicReference<String> executedQuery = new AtomicReference<>();
        AgentChatService defaultAgentService = (agentId, versionId, query, context) -> {
            assertEquals(60L, agentId);
            assertEquals(70L, versionId);
            executedQuery.set(query);
            return "默认Agent回答";
        };
        ApplicationChatExecutionServiceImpl service = new ApplicationChatExecutionServiceImpl(
                (definition, request) -> {
                    throw new AssertionError("默认Agent不应进入WorkflowExecutor");
                },
                defaultAgentService,
                agentRuntimeService(),
                new ChatExecutionContextRegistry(),
                conversationContextService(),
                (context, response) -> {
                    throw new AssertionError("未配置意图树时不应调用意图路由");
                },
                workflowRepo(null),
                plannerCoordinator());

        ApplicationChatResult result = service.execute(
                snapshot,
                "普通问题",
                new LoginUser(7L, "tester", "测试用户", null),
                new ChatTurnBO(10L, 11L, 12L, 3),
                null,
                ChatExecutionEventPublisher.noop());

        assertEquals("默认Agent回答", result.answer());
        assertEquals("普通问题", executedQuery.get());
        assertEquals(99L, result.modelInstanceId());
    }

    @Test
    void shouldExecuteDefaultAgentWhenOptionalFastRouteDoesNotMatch() {
        AtomicReference<String> executedQuery = new AtomicReference<>();
        ApplicationChatExecutionServiceImpl service = new ApplicationChatExecutionServiceImpl(
                (definition, request) -> {
                    throw new AssertionError("快速路由未命中不应进入Workflow");
                },
                (agentId, versionId, query, context) -> {
                    executedQuery.set(query);
                    return "默认Agent回答";
                },
                agentRuntimeService(),
                new ChatExecutionContextRegistry(),
                conversationContextService(),
                (context, response) -> IntentRoutingResult.noMatch(context.routingQuery()),
                workflowRepo(null),
                plannerCoordinator());

        ApplicationChatResult result = service.execute(
                v5FastRouteSnapshot(false),
                "组合问题",
                new LoginUser(7L, "tester", "测试用户", null),
                new ChatTurnBO(10L, 11L, 12L, 3),
                null,
                ChatExecutionEventPublisher.noop());

        assertEquals("默认Agent回答", result.answer());
        assertEquals("组合问题", executedQuery.get());
    }

    @Test
    void shouldUsePlannerWhenOptionalFastRouteDoesNotMatch() {
        PlannerRuntimeCoordinator coordinator = new PlannerRuntimeCoordinator() {
            @Override
            public PlannerContextResolution resolveInteraction(
                    ConversationContext context,
                    com.lingxi.workbench.chat.model.request.ClarificationResponseRequest response) {
                return new PlannerContextResolution(context, false, java.util.Set.of());
            }

            @Override
            public String startRun(ConversationContext context) {
                return "run-fast-route";
            }

            @Override
            public PlanExecutionResult execute(PlannerRuntimeRequest request) {
                assertEquals("run-fast-route", request.getRunId());
                return new PlanExecutionResult("Planner回答", 81L, List.of());
            }
        };
        ApplicationChatExecutionServiceImpl service = new ApplicationChatExecutionServiceImpl(
                (definition, request) -> {
                    throw new AssertionError("Planner回落不应进入Workflow");
                },
                (agentId, versionId, query, context) -> {
                    throw new AssertionError("启用Planner时不应直接执行默认Agent");
                },
                agentRuntimeService(),
                new ChatExecutionContextRegistry(),
                conversationContextService(),
                (context, response) -> IntentRoutingResult.noMatch(context.routingQuery()),
                workflowRepo(null),
                coordinator);

        ApplicationChatResult result = service.execute(
                v5FastRouteSnapshot(true),
                "复杂组合问题",
                new LoginUser(7L, "tester", "测试用户", null),
                new ChatTurnBO(10L, 11L, 12L, 3),
                null,
                ChatExecutionEventPublisher.noop());

        assertEquals("Planner回答", result.answer());
        assertEquals(81L, result.modelInstanceId());
    }

    @Test
    void shouldUsePlannerWhenEnabledAndIntentRoutingIsAbsent() {
        ApplicationAgentReference defaultAgent = new ApplicationAgentReference(60L, 70L, "默认助手", "处理常规请求");
        ApplicationCapabilityDefinition capability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(60L)
                .targetVersionId(70L)
                .build();
        ApplicationRuntimeSnapshot snapshot = new ApplicationRuntimeSnapshot(
                1L,
                "support",
                30L,
                ApplicationDefinition.builder()
                        .schemaVersion(4)
                        .defaultAgent(defaultAgent)
                        .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(capability)).build())
                        .plannerPolicy(PlannerPolicy.builder().enabled(true).chatModelInstanceId(80L).promptVersionId(90L)
                                .maxSteps(6).maxReplans(1).maxModelCalls(6).maxToolCalls(6).tokenBudget(12000).timeoutSeconds(60).build())
                        .build());
        ChatExecutionContextRegistry registry = new ChatExecutionContextRegistry();
        PlannerRuntimeCoordinator coordinator = new PlannerRuntimeCoordinator() {
            @Override
            public PlannerContextResolution resolveInteraction(ConversationContext context, com.lingxi.workbench.chat.model.request.ClarificationResponseRequest response) {
                return new PlannerContextResolution(context, false, java.util.Set.of());
            }

            @Override
            public String startRun(ConversationContext context) {
                return "run-1";
            }

            @Override
            public PlanExecutionResult execute(PlannerRuntimeRequest request) {
                assertEquals("run-1", request.getRunId());
                assertEquals("复杂问题", request.getConversationContext().routingQuery());
                assertEquals("run-1", request.getExecutionContext().getExecutionId());
                return new PlanExecutionResult("规划回答", 81L, List.of());
            }
        };
        ApplicationChatExecutionServiceImpl service = new ApplicationChatExecutionServiceImpl(
                (definition, request) -> {
                    throw new AssertionError("Planner路径不应进入兼容Workflow");
                },
                (agentId, versionId, query, context) -> {
                    throw new AssertionError("Planner路径不应直接执行默认Agent");
                },
                agentRuntimeService(),
                registry,
                conversationContextService(),
                (context, response) -> {
                    throw new AssertionError("未配置意图树时不应调用意图路由");
                },
                workflowRepo(null),
                coordinator);

        ApplicationChatResult result = service.execute(
                snapshot,
                "复杂问题",
                new LoginUser(7L, "tester", "测试用户", null),
                new ChatTurnBO(10L, 11L, 12L, 3),
                null,
                ChatExecutionEventPublisher.noop());

        assertEquals("规划回答", result.answer());
        assertEquals(81L, result.modelInstanceId());
        assertThrows(WorkflowExecutionException.class, () -> registry.require("run-1"));
    }

    private static AgentChatService agentChatService() {
        return (agentId, versionId, query, context) -> "Agent回答";
    }

    private static PlannerRuntimeCoordinator plannerCoordinator() {
        return new PlannerRuntimeCoordinator() {
            @Override
            public PlannerContextResolution resolveInteraction(
                    ConversationContext context,
                    com.lingxi.workbench.chat.model.request.ClarificationResponseRequest response) {
                return new PlannerContextResolution(context, false, java.util.Set.of());
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
                .runtimeVariables(initializer.initialize(loginUser, query, turn.conversationId()))
                .preparationTokenUsage(preparationUsage)
                .build();
    }

    private static ContextBudget contextBudget() {
        return ContextBudget.builder().maxHistoryRounds(10).maxSystemPromptTokens(4000).maxCapabilityTokens(4000)
                .maxHistoryTokens(4000).maxMemoryTokens(4000).maxInputTokens(4000).maxObservationTokens(4000)
                .reservedOutputTokens(4000).build();
    }

    private static AgentRuntimeService agentRuntimeService() {
        AgentRuntimeDefinition definition = AgentRuntimeDefinition.builder()
                .schemaVersion(1)
                .agentType("RAG_CHAT")
                .chatModelInstanceId(99L)
                .answerPromptVersionId(88L)
                .build();
        return (agentId, versionId) ->
                new AgentRuntimeSnapshot(agentId, versionId, definition);
    }

    private static ApplicationRuntimeSnapshot snapshot() {
        WorkflowNodeDefinition agent = new WorkflowNodeDefinition(
                "agent",
                "Agent",
                WorkflowNodeType.AGENT,
                new AgentNodeConfig(
                        10L,
                        20L,
                        Map.of("query", "query"),
                        Map.of("answer", "answer")));
        WorkflowDefinition workflow =
                new WorkflowDefinition(1, "main", List.of(agent), List.of());
        return new ApplicationRuntimeSnapshot(
                1L, "support", 30L, new ApplicationDefinition(1, workflow));
    }

    private static ApplicationRuntimeSnapshot v5FastRouteSnapshot(boolean plannerEnabled) {
        ApplicationAgentReference defaultAgent = new ApplicationAgentReference(60L, 70L, "默认助手", "处理常规请求");
        ApplicationCapabilityDefinition defaultCapability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(60L)
                .targetVersionId(70L)
                .build();
        ApplicationCapabilityDefinition workflowCapability = ApplicationCapabilityDefinition.builder()
                .code("refund_workflow")
                .type(ApplicationCapabilityType.WORKFLOW)
                .targetId(40L)
                .targetVersionId(50L)
                .build();
        ApplicationIntentMapping mapping = ApplicationIntentMapping.builder()
                .intentCode("refund")
                .targetType(IntentExecutionTargetType.WORKFLOW)
                .capabilityCode("refund_workflow")
                .targetId(40L)
                .targetVersionId(50L)
                .build();
        PlannerPolicy plannerPolicy = plannerEnabled
                ? PlannerPolicy.builder().enabled(true).chatModelInstanceId(80L).promptVersionId(90L)
                .maxSteps(6).maxReplans(1).maxModelCalls(6).maxToolCalls(6).tokenBudget(12000).timeoutSeconds(60).build()
                : PlannerPolicy.disabled();
        return new ApplicationRuntimeSnapshot(1L, "support", 30L, ApplicationDefinition.builder()
                .schemaVersion(5)
                .defaultAgent(defaultAgent)
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(defaultCapability, workflowCapability))
                        .build())
                .optionalFastRoutes(ApplicationIntentBinding.builder()
                        .enabled(true)
                        .intentTreeId(2L)
                        .intentTreeVersionId(20L)
                        .mappings(List.of(mapping))
                        .build())
                .plannerPolicy(plannerPolicy)
                .build());
    }

    private static WorkflowRuntimeRepo workflowRepo(WorkflowDefinition definition) {
        return new WorkflowRuntimeRepo(null) {
            @Override
            public Optional<RuntimeWorkflowVersionEntity> findVersion(Long workflowId, Long versionId) {
                if (definition == null || !Long.valueOf(40L).equals(workflowId) || !Long.valueOf(50L).equals(versionId)) {
                    return Optional.empty();
                }
                RuntimeWorkflowVersionEntity entity = new RuntimeWorkflowVersionEntity();
                entity.setId(versionId);
                entity.setWorkflowId(workflowId);
                entity.setDefinitionSnapshot(definition);
                return Optional.of(entity);
            }
        };
    }
}
