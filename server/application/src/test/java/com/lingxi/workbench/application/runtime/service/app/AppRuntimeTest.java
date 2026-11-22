package com.lingxi.workbench.application.runtime.service.app;

import com.lingxi.workbench.agentruntime.model.AgentRunCancelledException;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.model.AppExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.service.agent.AgentVersionService;
import com.lingxi.workbench.application.runtime.service.capability.AppCapabilityDispatcher;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.routing.IntentRoutingService;
import com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService;
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
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import org.junit.jupiter.api.Test;
import io.micrometer.observation.ObservationRegistry;
import com.lingxi.workbench.observability.service.StageObserver;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class AppRuntimeTest {

    @Test
    void shouldReturnFixedResponseWithoutStartingExecution() {
        AppRuntime service = service(
                unexpectedCapabilityService(), conversationContextService(),
                (context, response) -> IntentRoutingResult.builder()
                        .action(IntentRoutingAction.FIXED_RESPONSE)
                        .response("固定回答")
                        .tokenUsage(TokenUsage.of(3, 2, 5))
                        .modelCalls(1)
                        .build(),
                plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(intentRoutingSnapshot(), "固定问题"));

        assertEquals("固定回答", result.answer());
        assertEquals(TokenUsage.of(3, 2, 5), result.tokenUsage());
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
        AppRuntime service = service(
                unexpectedCapabilityService(), conversationContextService(),
                (context, response) -> IntentRoutingResult.builder()
                        .action(IntentRoutingAction.CLARIFICATION)
                        .response("请选择需要办理的业务")
                        .clarification(clarification)
                        .build(),
                plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(intentRoutingSnapshot(), "办理业务", eventPublisher));

        assertEquals("请选择需要办理的业务", result.answer());
        verify(eventPublisher).publishClarification(clarification);
    }

    @Test
    void shouldExecuteWorkflowIntentRoutingThroughPublishedCapability() {
        ApplicationIntentMapping mapping = mapping(
                IntentExecutionTargetType.WORKFLOW, "refund_workflow", 40L, 50L);
        CapabilityDefinition capability = capability(
                "refund_workflow", ApplicationCapabilityType.WORKFLOW, 40L, 50L);
        MemoryConfig memory = MemoryConfig.builder()
                .embeddingModelInstanceId(70L)
                .interactionRecord(InteractionRecordMemoryConfig.builder()
                        .enabled(true)
                        .build())
                .build();
        AppRuntimeSnapshot snapshot = routedSnapshot(mapping, capability, memory, false);
        AppCapabilityDispatcher capabilityService = dispatcher(request -> {
            assertEquals("refund_workflow", request.getCapability().getCode());
            assertEquals("退款问题", request.getInputs().getString("query"));
            assertEquals("测试用户", request.getRun().conversation()
                    .getRuntimeVariables().require(RuntimeVariableAccessors.USER_NAME));
            assertEquals(14, request.getRun().budget().remainingModelCalls());
            request.getRun().budget().recordUsage(TokenUsage.of(5, 1, 6));
            return new CapabilityExecutionResult("退款回答", 99L, List.of());
        });
        AppRuntime service = service(
                capabilityService, conversationContextService(TokenUsage.of(10, 2, 12)),
                (context, response) -> IntentRoutingResult.builder()
                        .action(IntentRoutingAction.EXECUTE_WORKFLOW)
                        .intentCode(mapping.getIntentCode())
                        .mapping(mapping)
                        .executionQuery(context.routingQuery())
                        .tokenUsage(TokenUsage.of(3, 1, 4))
                        .modelCalls(2)
                        .build(), plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(snapshot, "退款问题"));

        assertEquals("退款回答", result.answer());
        assertEquals(99L, result.modelInstanceId());
        assertEquals(TokenUsage.of(18, 4, 22), result.tokenUsage());
    }

    @Test
    void shouldExecuteAgentIntentRoutingThroughPublishedCapability() {
        ApplicationIntentMapping mapping = mapping(
                IntentExecutionTargetType.AGENT, "consult_agent", 60L, 70L);
        CapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.AGENT, 60L, 70L);
        AtomicReference<CapabilityExecutionRequest> executed = new AtomicReference<>();
        AppRuntime service = service(
                dispatcher(request -> {
                    executed.set(request);
                    return new CapabilityExecutionResult("Agent回答", 88L, List.of());
                }),
                conversationContextService(), routed(mapping), plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(
                routedSnapshot(mapping, capability, MemoryConfig.disabled(), false), "咨询问题"));

        assertEquals("Agent回答", result.answer());
        assertEquals("consult_agent", executed.get().getCapability().getCode());
        assertEquals("咨询问题", executed.get().getInputs().getString("query"));
    }

    @Test
    void shouldValidateExplicitSkillAgainstDirectSpecialistVersionBeforeExecution() {
        ApplicationIntentMapping mapping = mapping(
                IntentExecutionTargetType.AGENT, "consult_agent", 60L, 70L);
        CapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.AGENT, 60L, 70L);
        AtomicReference<CapabilityExecutionRequest> executed = new AtomicReference<>();
        AppRuntime service = service(dispatcher(request -> {
            executed.set(request);
            return new CapabilityExecutionResult("显式Skill回答", 88L, List.of());
        }), conversationContextService(), routed(mapping), plannerDisabled());
        AppExecutionRequest request = AppExecutionRequest.builder()
                .application(routedSnapshot(mapping, capability, MemoryConfig.disabled(), false))
                .query("核验事实").loginUser(new LoginUser(7L, "tester", "测试用户", null))
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .explicitSkillCodes(List.of("fact_check"))
                .eventPublisher(ChatExecutionEventPublisher.noop()).build();

        ApplicationChatResult result = service.execute(request);

        assertEquals("显式Skill回答", result.answer());
        assertEquals(List.of("fact_check"), executed.get().getRun().request().getExplicitSkillCodes());
    }

    @Test
    void shouldRejectExplicitSkillForWorkflowTarget() {
        ApplicationIntentMapping mapping = mapping(
                IntentExecutionTargetType.WORKFLOW, "refund_workflow", 40L, 50L);
        CapabilityDefinition capability = capability(
                "refund_workflow", ApplicationCapabilityType.WORKFLOW, 40L, 50L);
        AppRuntime service = service(unexpectedCapabilityService(),
                conversationContextService(), routed(mapping), plannerDisabled());
        AppExecutionRequest request = AppExecutionRequest.builder()
                .application(routedSnapshot(mapping, capability, MemoryConfig.disabled(), false))
                .query("退款问题").loginUser(new LoginUser(7L, "tester", "测试用户", null))
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .explicitSkillCodes(List.of("fact_check"))
                .eventPublisher(ChatExecutionEventPublisher.noop()).build();

        assertThrows(BusinessException.class, () -> service.execute(request));
    }

    @Test
    void shouldExecuteReservedDefaultAgentCapabilityWhenIntentRoutingIsDisabled() {
        AtomicReference<CapabilityExecutionRequest> executed = new AtomicReference<>();
        IntentRoutingService routingService = (context, response) ->
                IntentRoutingResult.skipped(context.routingQuery());
        AppRuntime service = service(
                dispatcher(request -> {
                    executed.set(request);
                    return new CapabilityExecutionResult("默认回答", 77L, List.of());
                }),
                conversationContextService(), routingService, plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(defaultSnapshot(), "普通问题"));

        assertEquals("默认回答", result.answer());
        assertEquals("default_agent", executed.get().getCapability().getCode());
        assertEquals("普通问题", executed.get().getInputs().getString("query"));
    }

    @Test
    void shouldReturnBudgetBoundaryMessageWithoutCallingCapabilityAfterContextOverrun() {
        AppRuntimeSnapshot snapshot = defaultSnapshot();
        snapshot.definition().setRuntimePolicy(ApplicationRuntimePolicy.builder()
                .maxModelCalls(2)
                .maxToolCalls(0)
                .maxDelegations(0)
                .totalTokenBudget(10)
                .tokenGracePercent(10)
                .build());
        AppRuntime service = service(
                unexpectedCapabilityService(),
                conversationContextService(TokenUsage.of(8, 4, 12)),
                (context, response) -> IntentRoutingResult.skipped(context.routingQuery()), plannerDisabled());

        ApplicationChatResult result = service.execute(executionRequest(snapshot, "普通问题"));

        assertTrue(result.answer().contains("预算边界"));
        assertEquals(TokenUsage.of(8, 4, 12), result.tokenUsage());
    }

    @Test
    void shouldExecuteConfiguredFallbackCapabilityWhenIntentRoutingDoesNotMatch() {
        AppRuntime service = service(
                dispatcher(request -> new CapabilityExecutionResult("Fallback回答", 81L, List.of())),
                conversationContextService(),
                (context, response) -> IntentRoutingResult.deferUnmatched(context.routingQuery(),
                        com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason.NO_CANDIDATE), plannerDisabled());

        ApplicationIntentMapping mapping = mapping(
                IntentExecutionTargetType.AGENT, "consult_agent", 61L, 71L);
        CapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.AGENT, 61L, 71L);
        AppRuntimeSnapshot snapshot = routedSnapshot(
                mapping, capability, MemoryConfig.disabled(), true);

        ApplicationChatResult result = service.execute(executionRequest(snapshot, "组合问题"));

        assertEquals("Fallback回答", result.answer());
        assertEquals(81L, result.modelInstanceId());
    }

    @Test
    void shouldRejectIntentRoutingWithoutCapabilityCode() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, null, 60L, 70L);

        assertThrows(IllegalArgumentException.class, () -> routedSnapshot(
                mapping, capability("consult_agent", ApplicationCapabilityType.AGENT, 60L, 70L),
                MemoryConfig.disabled(), false));
    }

    @Test
    void shouldRejectCapabilityMissingFromPublishedCatalog() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, "missing_agent", 60L, 70L);

        assertThrows(IllegalArgumentException.class, () -> routedSnapshot(
                mapping, capability("other_agent", ApplicationCapabilityType.AGENT, 60L, 70L),
                MemoryConfig.disabled(), false));
    }

    @Test
    void shouldRejectCapabilityTypeInconsistentWithRouteAction() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, "consult_agent", 60L, 70L);
        CapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.WORKFLOW, 60L, 70L);

        assertThrows(IllegalArgumentException.class, () -> routedSnapshot(
                mapping, capability, MemoryConfig.disabled(), false));
    }

    @Test
    void shouldRejectRouteTargetInconsistentWithPublishedCapability() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, "consult_agent", 60L, 70L);
        CapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.AGENT, 61L, 71L);

        assertThrows(IllegalArgumentException.class, () -> routedSnapshot(
                mapping, capability, MemoryConfig.disabled(), false));
    }

    @Test
    void shouldPersistFailedRunStatusWhenRuntimeFails() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        AppRuntime service = service(
                dispatcher(request -> {
                    throw new IllegalStateException("runtime failed");
                }),
                conversationContextService(),
                (context, response) -> IntentRoutingResult.skipped(context.routingQuery()),
                plannerDisabled(), persistenceService);

        assertThrows(IllegalStateException.class,
                () -> service.execute(executionRequest(defaultSnapshot(), "超时问题")));

        verify(persistenceService).changeRunStatus(anyString(), eq(ExecutionRunStatus.RUNNING),
                eq(ExecutionRunStatus.FAILED), eq("TERMINAL"));
    }

    @Test
    void shouldPersistCancelledRunStatusForExplicitRuntimeCancellation() {
        ExecutionPersistenceService persistenceService = mock(ExecutionPersistenceService.class);
        AppRuntime service = service(
                dispatcher(request -> {
                    throw new AgentRunCancelledException();
                }),
                conversationContextService(),
                (context, response) -> IntentRoutingResult.skipped(context.routingQuery()),
                plannerDisabled(), persistenceService);

        assertThrows(AgentRunCancelledException.class,
                () -> service.execute(executionRequest(defaultSnapshot(), "取消问题")));

        verify(persistenceService).changeRunStatus(anyString(), eq(ExecutionRunStatus.RUNNING),
                eq(ExecutionRunStatus.CANCELLED), eq("TERMINAL"));
    }

    @Test
    void shouldRejectIncompleteExecutionRequest() {
        AppRuntime service = service(
                unexpectedCapabilityService(), conversationContextService(),
                (context, response) -> IntentRoutingResult.skipped(context.routingQuery()), plannerDisabled());
        AppExecutionRequest request = AppExecutionRequest.builder()
                .application(defaultSnapshot())
                .query("问题")
                .build();

        assertThrows(BusinessException.class, () -> service.execute(null));
        assertThrows(BusinessException.class, () -> service.execute(request));
    }

    private static AppRuntime service(AppCapabilityDispatcher capabilityService,
                                                               ConversationContextService contextService,
                                                               IntentRoutingService routingService,
                                                               Object ignoredSupervisorRuntime) {
        return service(capabilityService, contextService, routingService,
                ignoredSupervisorRuntime, mock(ExecutionPersistenceService.class));
    }

    private static AppRuntime service(AppCapabilityDispatcher capabilityService,
                                                                ConversationContextService contextService,
                                                                IntentRoutingService routingService,
                                                                Object ignoredSupervisorRuntime,
                                                                ExecutionPersistenceService persistenceService) {
        InteractionSessionService interactionSessionService = mock(InteractionSessionService.class);
        when(interactionSessionService.resolve(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        AgentVersionService agentVersionService = mock(AgentVersionService.class);
        when(agentVersionService.resolveVersion(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> agentSnapshot(
                        invocation.getArgument(0), invocation.getArgument(1)));
        return new AppRuntime(
                capabilityService, contextService, routingService,
                interactionSessionService, agentVersionService,
                new StageObserver(ObservationRegistry.NOOP), persistenceService,
                new RuntimeVariablesInitializer(java.time.Clock.systemUTC(), "Lingxi", java.util.Locale.CHINA));
    }

    private static AppCapabilityDispatcher unexpectedCapabilityService() {
        return dispatcher(request -> {
            throw new AssertionError("非法发布快照不应进入能力执行服务");
        });
    }

    private static AppCapabilityDispatcher dispatcher(
            Function<CapabilityExecutionRequest, CapabilityExecutionResult> execution) {
        AppCapabilityDispatcher dispatcher = mock(AppCapabilityDispatcher.class);
        when(dispatcher.execute(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> execution.apply(invocation.getArgument(0)));
        return dispatcher;
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

    private static AppExecutionRequest executionRequest(AppRuntimeSnapshot snapshot, String query) {
        return executionRequest(snapshot, query, ChatExecutionEventPublisher.noop());
    }

    private static AppExecutionRequest executionRequest(AppRuntimeSnapshot snapshot, String query,
                                                                    ChatExecutionEventPublisher eventPublisher) {
        return AppExecutionRequest.builder()
                .application(snapshot)
                .query(query)
                .loginUser(new LoginUser(7L, "tester", "测试用户", null))
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .eventPublisher(eventPublisher)
                .build();
    }

    private static Object plannerDisabled() {
        return new Object();
    }

    private static ConversationContextService conversationContextService() {
        return conversationContextService(TokenUsage.empty());
    }

    private static ConversationContextService conversationContextService(TokenUsage preparationUsage) {
        RuntimeVariablesInitializer initializer = new RuntimeVariablesInitializer("Lingxi Workbench");
        return input -> ConversationContext.builder()
                .applicationSnapshot(input.application())
                .loginUser(input.loginUser())
                .turn(input.turn())
                .originalQuery(input.query())
                .rewrittenQuery(input.query())
                .recentMessages(List.of())
                .summary("")
                .longTermMemory("")
                .budget(contextBudget())
                .runtimeVariables(runtimeVariables(initializer, input.loginUser(), input.query(), input.turn()))
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
                .collaboration(collaboration())
                .memoryConf(MemoryConfig.disabled())
                .contextPolicy(ApplicationContextPolicy.defaults())
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(fallbackCap()))
                        .build())
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .build();
        return new AppRuntimeSnapshot(1L, "support", 30L, definition);
    }

    private static AppRuntimeSnapshot intentRoutingSnapshot() {
        ApplicationIntentMapping mapping = mapping(IntentExecutionTargetType.AGENT, "consult_agent", 61L, 71L);
        CapabilityDefinition capability = capability(
                "consult_agent", ApplicationCapabilityType.AGENT, 61L, 71L);
        return routedSnapshot(mapping, capability, MemoryConfig.disabled(), false);
    }

    private static AppRuntimeSnapshot routedSnapshot(ApplicationIntentMapping mapping,
                                                     CapabilityDefinition mappedCapability,
                                                     MemoryConfig memory, boolean plannerEnabled) {
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(collaboration())
                .contextPolicy(ApplicationContextPolicy.defaults())
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder()
                        .capabilities(List.of(fallbackCap(), mappedCapability))
                        .build())
                .intentRouting(ApplicationIntentBinding.builder()
                        .enabled(true)
                        .intentTreeId(2L)
                        .intentTreeVersionId(20L)
                        .mappings(List.of(mapping))
                        .build())
                .memoryConf(memory)
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .build();
        return new AppRuntimeSnapshot(1L, "support", 30L, definition);
    }

    private static AgentCollaborationDefinition collaboration() {
        return new AgentCollaborationDefinition(
                new ApplicationAgentReference(60L, 70L, "默认助手", "负责收口"),
                List.of(), List.of(), List.of());
    }

    private static CapabilityDefinition fallbackCap() {
        return capability("default_agent", ApplicationCapabilityType.AGENT, 60L, 70L);
    }

    private static CapabilityDefinition capability(String code, ApplicationCapabilityType type,
                                                   Long targetId, Long targetVersionId) {
        return CapabilityDefinition.builder()
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

    private static AgentRuntimeSnapshot agentSnapshot(Long agentId, Long agentVersionId) {
        return new AgentRuntimeSnapshot(agentId, agentVersionId, AgentRuntimeDefinition.builder()
                .schemaVersion(com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION)
                .role(AgentRole.SPECIALIST)
                .chatModelInstanceId(80L)
                .toolCodes(List.of())
                .skills(List.of(AgentSkillBindingSnapshot.builder().skillId(1L).skillVersionId(11L)
                        .versionNo(1).checksum("checksum")
                        .skill(SkillVersionSnapshot.builder().schemaVersion(1).code("fact_check")
                                .name("事实核验").description("核验事实").instructions("先核验再回答")
                                .activationMode(SkillActivationMode.EXPLICIT_ONLY)
                                .requestedToolCodes(List.of()).build())
                        .effectiveToolCodes(List.of()).deniedToolCodes(List.of()).build()))
                .build());
    }
}
