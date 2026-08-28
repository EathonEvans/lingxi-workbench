package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.agentruntime.AgentRuntime;
import com.lingxi.workbench.agentruntime.gateway.AgentScopeSpecialistStreamingGateway;
import com.lingxi.workbench.agentruntime.gateway.AgentScopeSupervisorGateway;
import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.model.ExecutionBudget;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityInvoker;
import com.lingxi.workbench.agentruntime.plan.RuntimeClarification;
import com.lingxi.workbench.agentruntime.skill.AgentScopePublishedSkillCatalog;
import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.application.runtime.model.ApplicationExecutionBudget;
import com.lingxi.workbench.application.runtime.model.ApplicationChatExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationRun;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeEventSinkFactory;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService;
import com.lingxi.workbench.application.runtime.service.skill.SkillRuntimeService;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.application.runtime.tool.KnowledgeSearchAgentToolFactory;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentExecutionAdapterTest {

    private static AgentVersionService agentVersionService(AgentRuntimeDefinition definition) {
        AgentVersionService service = mock(AgentVersionService.class);
        when(service.resolveVersion(any(), any())).thenAnswer(invocation -> new AgentRuntimeSnapshot(
                invocation.getArgument(0), invocation.getArgument(1), definition));
        return service;
    }

    @Test
    void shouldRunLockedAgentVersionThroughExistingChatChain() {
        AgentRuntimeDefinition definition = definition();
        ApplicationRun run = run("run-1", runtimeVariables(), new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()),
                ContextBudgetPolicy.builder().reservedOutputTokens(2048).build());
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AtomicReference<RuntimeContext> capturedRuntimeContext = new AtomicReference<>();

        AgentExecutionAdapter service = service(
                definition,
                AgentExecutionAdapterTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(
                            new ChatStreamChunk("工作流"),
                            new ChatStreamChunk("回答"),
                            new ChatStreamChunk("", TokenUsage.of(12, 4, 16)));
                },
                toolCodes -> List.of(), capturedRuntimeContext);

        String answer = service.run(10L, 20L, "如何退款", run).content();

        assertEquals("工作流回答", answer);
        assertEquals("你是售后助手", captured.get().systemPrompt());
        assertEquals(0.2D, captured.get().modelConfig().getTemperature());
        assertEquals(2048, captured.get().modelConfig().getMaxTokens());
        assertEquals(
                "disabled",
                captured.get().modelConfig().getExtensionParameters()
                        .getJSONObject("thinking")
                        .getString("type"));
        assertEquals(16, run.budget().tokenUsage().totalTokens());
        assertFalse(captured.get().history().isEmpty());
        assertEquals("7", capturedRuntimeContext.get().getUserId());
        assertEquals("run-1", capturedRuntimeContext.get().getSessionId());
        assertEquals(captured.get().history().size() + 1,
                capturedRuntimeContext.get().getAgentState().getContext().size());
    }

    @Test
    void shouldRejectAgentVersionWithoutUserPrompt() {
        AgentRuntimeDefinition definition = definition();
        definition.setUserPromptVersionId(null);
        AgentExecutionAdapter service = service(
                definition,
                AgentExecutionAdapterTest::promptSnapshotByVersion,
                command -> Flux.empty(),
                toolCodes -> List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.run(
                        10L,
                        20L,
                        "如何退款",
                        run("run-invalid", runtimeVariables(), new ChatTurnBO(10L, 11L, 12L, 3),
                                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()))));

        assertEquals("AgentVersion缺少模型、SYSTEM Prompt或USER Prompt", exception.getMessage());
    }

    @Test
    void shouldCreateIsolatedVariablesForNodeQuery() {
        ApplicationRun run = run("run-query-mismatch", runtimeVariables(),
                new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()));
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentExecutionAdapter service = service(
                definition(),
                AgentExecutionAdapterTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("节点回答"));
                },
                toolCodes -> List.of());

        String answer = service.run(10L, 20L, "不同的问题", run).content();

        assertEquals("节点回答", answer);
        assertEquals("不同的问题", captured.get().prompt());
        assertEquals("如何退款", run.conversation().getRuntimeVariables()
                .require(RuntimeVariableAccessors.USER_QUERY));
    }

    @Test
    void shouldDisableToolsAndFinalizeWhenApplicationToolBudgetIsUnavailable() {
        AgentRuntimeDefinition definition = definition();
        definition.setToolCodes(List.of("system_current_time"));
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentExecutionAdapter service = service(
                definition,
                AgentExecutionAdapterTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("已基于现有上下文回答"));
                },
                toolCodes -> List.of(new StubAgentTool()));
        ApplicationRun run = run("run-invalid-tool-limit", runtimeVariables(),
                new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(3)
                        .maxToolCalls(0)
                        .maxDelegations(0)
                        .totalTokenBudget(8000)
                        .build()));

        String answer = service.run(10L, 20L, "如何退款", run).content();

        assertEquals("已基于现有上下文回答", answer);
        assertTrue(captured.get().toolRuntimeConfig().isEmpty());
        assertTrue(captured.get().systemPrompt().contains("本次不再开放工具、委派或规划"));
    }

    @Test
    void shouldLeaveToolPolicyInjectionToAgentScopeRuntime() {
        AgentRuntimeDefinition definition = definition();
        definition.setToolCodes(List.of("system_current_time"));
        definition.setToolPolicy(new AgentRuntimeDefinition.ToolPolicy(1000));
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentExecutionAdapter service = service(
                definition,
                AgentExecutionAdapterTest::toolConflictPromptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("现在是10点"));
                },
                toolCodes -> List.of(new StubAgentTool()));
        ApplicationRun run = run("run-with-tool", runtimeVariables("现在是几点"),
                new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()));

        String answer = service.run(10L, 20L, "现在是几点", run).content();

        assertEquals("现在是10点", answer);
        assertTrue(captured.get().toolRuntimeConfig().isPresent());
        assertFalse(captured.get().systemPrompt().contains("平台工具调用规则"));
    }

    @Test
    void shouldKeepToolBudgetIndependentFromRemainingModelCalls() {
        AgentRuntimeDefinition definition = definition();
        definition.setToolCodes(List.of("system_current_time"));
        definition.setToolPolicy(new AgentRuntimeDefinition.ToolPolicy(1000));
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AtomicReference<ExecutionBudget> capturedBudget = new AtomicReference<>();
        Function<ChatStreamCommand, Flux<ChatStreamChunk>> gateway = command -> {
            captured.set(command);
            return Flux.just(new ChatStreamChunk("已有答案"));
        };
        ExecutionPersistenceService persistenceService = testPersistenceService();
        AgentExecutionAdapter service = new AgentExecutionAdapter(
                agentVersionService(definition),
                runtime(gateway, capturedBudget),
                AgentExecutionAdapterTest::promptSnapshotByVersion,
                modelRequestFactory(),
                modelService(),
                toolCodes -> List.of(new StubAgentTool()),
                mock(KnowledgeSearchAgentToolFactory.class),
                mock(com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore.class),
                emptySkillRuntimeService(),
                persistenceService,
                new ApplicationRuntimeEventSinkFactory(persistenceService,
                        io.micrometer.observation.ObservationRegistry.NOOP),
                mock(com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeTaskStore.class),
                mock(com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService.class),
                new PromptTemplateRenderer(),
                mock(org.springframework.ai.tokenizer.TokenCountEstimator.class));
        ApplicationRun run = run("run-independent-budgets", runtimeVariables(),
                new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(3)
                        .maxDelegations(0)
                        .totalTokenBudget(8000)
                        .build()));

        service.run(10L, 20L, "如何退款", run);

        assertTrue(captured.get().toolRuntimeConfig().isPresent());
        assertEquals(3, capturedBudget.get().policy().getMaxToolCalls());
        assertEquals(2, capturedBudget.get().policy().getMaxModelCalls());
    }

    @Test
    void shouldKeepPublishedModelResponseIdleTimeoutWithoutApplicationDeadlineClamp() {
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentExecutionAdapter service = service(
                definition(),
                AgentExecutionAdapterTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("按时回答"));
                },
                toolCodes -> List.of());
        ApplicationRun run = run("run-timeout", runtimeVariables(), new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(0)
                        .maxDelegations(0)
                        .totalTokenBudget(8000)
                        .build()));

        service.run(10L, 20L, "如何退款", run);

        assertEquals(120, captured.get().modelConfig().getTimeoutSeconds());
    }

    @Test
    void shouldAllowAgentWithoutToolRuntime() {
        AgentExecutionAdapter service = service(
                definition(),
                AgentExecutionAdapterTest::promptSnapshotByVersion,
                command -> Flux.just(new ChatStreamChunk("无宽限回答")),
                toolCodes -> List.of());
        ApplicationRun run = run("run-zero-timeout-grace", runtimeVariables(),
                new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(0)
                        .maxDelegations(0)
                        .totalTokenBudget(8000)
                        .build()));

        assertEquals("无宽限回答", service.run(10L, 20L, "如何退款", run).content());
    }

    @Test
    void shouldInjectFrozenKnowledgeSearchToolForReactAgent() {
        AgentRuntimeDefinition definition = reactDefinition();
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        KnowledgeSearchAgentToolFactory toolFactory = mock(KnowledgeSearchAgentToolFactory.class);
        when(toolFactory.create(definition.getKnowledgeSearch())).thenReturn(new KnowledgeSearchStubAgentTool());
        PromptRuntimeService promptRuntimeService = AgentExecutionAdapterTest::promptSnapshotByVersion;
        ExecutionPersistenceService persistenceService = testPersistenceService();
        AgentExecutionAdapter service = new AgentExecutionAdapter(
                agentVersionService(definition),
                runtime(command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("知识回答"));
                }),
                promptRuntimeService,
                modelRequestFactory(),
                modelService(),
                toolCodes -> List.of(),
                toolFactory,
                mock(com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore.class),
                emptySkillRuntimeService(),
                persistenceService,
                new ApplicationRuntimeEventSinkFactory(persistenceService,
                        io.micrometer.observation.ObservationRegistry.NOOP),
                mock(com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeTaskStore.class),
                mock(com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService.class),
                new PromptTemplateRenderer(),
                mock(org.springframework.ai.tokenizer.TokenCountEstimator.class));
        RuntimeVariables variables = runtimeVariables("退款条件");
        ApplicationRun run = run("run-react-knowledge", variables, new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()));

        String answer = service.run(10L, 20L, "退款条件", run).content();

        assertEquals("知识回答", answer);
        assertEquals("knowledge_search", captured.get().toolRuntimeConfig().orElseThrow().getTools().getFirst().definition().getCode());
        verify(toolFactory).create(definition.getKnowledgeSearch());
    }

    @Test
    void shouldPassPublishedSkillCatalogToAgentScopeRuntime() {
        AgentRuntimeDefinition definition = definition();
        SkillVersionSnapshot skill = SkillVersionSnapshot.builder()
                .schemaVersion(1)
                .code("fact_check")
                .name("事实核验")
                .instructions("回答前核验关键事实。")
                .activationMode(SkillActivationMode.ALWAYS_ON)
                .requestedToolCodes(List.of())
                .build();
        AgentSkillBindingSnapshot binding = AgentSkillBindingSnapshot.builder()
                .skillId(1L)
                .skillVersionId(2L)
                .versionNo(1)
                .checksum("checksum")
                .skill(skill)
                .effectiveToolCodes(List.of())
                .deniedToolCodes(List.of())
                .build();
        definition.setSkills(List.of(binding));
        SkillRuntimeService skillRuntimeService = mock(SkillRuntimeService.class);
        when(skillRuntimeService.load(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(new SkillRuntimeCatalog(List.of(binding), List.of()));
        ExecutionPersistenceService persistenceService = testPersistenceService();
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AtomicReference<ExecutionBudget> capturedBudget = new AtomicReference<>();
        AtomicReference<AgentScopePublishedSkillCatalog> capturedSkillCatalog = new AtomicReference<>();
        Function<ChatStreamCommand, Flux<ChatStreamChunk>> streamingChatGateway = command -> {
            captured.set(command);
            return Flux.just(new ChatStreamChunk("已核验"));
        };
        AgentExecutionAdapter service = new AgentExecutionAdapter(
                agentVersionService(definition),
                runtime(streamingChatGateway, capturedBudget, null, capturedSkillCatalog),
                AgentExecutionAdapterTest::promptSnapshotByVersion,
                modelRequestFactory(),
                modelService(),
                toolCodes -> List.of(),
                mock(KnowledgeSearchAgentToolFactory.class),
                mock(com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore.class),
                skillRuntimeService,
                persistenceService,
                new ApplicationRuntimeEventSinkFactory(persistenceService,
                        io.micrometer.observation.ObservationRegistry.NOOP),
                mock(com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeTaskStore.class),
                mock(com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService.class),
                new PromptTemplateRenderer(),
                mock(org.springframework.ai.tokenizer.TokenCountEstimator.class));
        ApplicationRun run = run("run-skill", runtimeVariables(), new ChatTurnBO(10L, 11L, 12L, 3),
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()),
                ContextBudgetPolicy.defaults());

        assertEquals("已核验", service.run(10L, 20L, "如何退款", run).content());
        assertFalse(captured.get().systemPrompt().contains("已激活Skill"));
        assertEquals(1, capturedBudget.get().modelCalls());
        assertEquals("回答前核验关键事实。", capturedSkillCatalog.get().repository()
                .getSkill("fact_check").getMetadataValue("instructions"));
    }

    @Test
    void shouldReturnClarificationQuestionAfterOpeningSupervisorClarification() {
        AgentRuntimeDefinition definition = definition();
        definition.setRole(AgentRole.SUPERVISOR);
        definition.setUserPromptVersionId(null);
        definition.setPlanPolicy(PlanPolicy.defaults());
        AgentRuntime agentRuntime = mock(AgentRuntime.class);
        RuntimeClarification clarification = RuntimeClarification.builder()
                .question("是否批准读取完整账号信息？")
                .options(List.of("批准", "不批准"))
                .build();
        when(agentRuntime.run(any(AgentRunRequest.class)))
                .thenReturn(new AgentRunResult.Clarification(clarification, TokenUsage.of(8, 3, 11), 4));
        InteractionSessionService interactionSessionService = mock(InteractionSessionService.class);
        com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO clarificationEvent =
                com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO.builder()
                        .clarificationId("100")
                        .question(clarification.getQuestion())
                        .build();
        when(interactionSessionService.open(any())).thenReturn(clarificationEvent);
        ExecutionPersistenceService persistenceService = testPersistenceService();
        AgentExecutionAdapter service = new AgentExecutionAdapter(
                agentVersionService(definition), agentRuntime,
                AgentExecutionAdapterTest::promptSnapshotByVersion, modelRequestFactory(), modelService(),
                toolCodes -> List.of(), mock(KnowledgeSearchAgentToolFactory.class),
                mock(com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore.class),
                emptySkillRuntimeService(), persistenceService,
                new ApplicationRuntimeEventSinkFactory(persistenceService,
                        io.micrometer.observation.ObservationRegistry.NOOP),
                mock(com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeTaskStore.class),
                interactionSessionService, new PromptTemplateRenderer(),
                mock(org.springframework.ai.tokenizer.TokenCountEstimator.class));
        CapabilityDefinition rootCapability = CapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.AGENT)
                .build();
        ApplicationDefinition applicationDefinition = mock(ApplicationDefinition.class);
        when(applicationDefinition.capabilityCatalog()).thenReturn(CapabilityCatalogSnapshot.builder()
                .capabilities(List.of(rootCapability))
                .build());
        AppRuntimeSnapshot application = mock(AppRuntimeSnapshot.class);
        when(application.definition()).thenReturn(applicationDefinition);
        ChatExecutionEventPublisher eventPublisher = mock(ChatExecutionEventPublisher.class);
        ApplicationChatExecutionRequest executionRequest = ApplicationChatExecutionRequest.builder()
                .application(application)
                .query("读取账号信息")
                .loginUser(loginUser())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .eventPublisher(eventPublisher)
                .build();
        ConversationContext conversation = mock(ConversationContext.class);
        when(conversation.getApplicationSnapshot()).thenReturn(application);
        when(conversation.getRuntimeVariables()).thenReturn(runtimeVariables("读取账号信息"));
        when(conversation.getBudget()).thenReturn(ContextBudgetPolicy.defaults());
        ApplicationRun run = new ApplicationRun("run-clarification", executionRequest, conversation,
                ApplicationExecutionBudget.fromRuntimePolicy(ApplicationRuntimePolicy.defaults()));
        CapabilityExecutionRequest request = CapabilityExecutionRequest.builder()
                .capability(rootCapability)
                .inputs(JSONObject.of("query", "读取账号信息"))
                .run(run)
                .build();

        CapabilityExecutionResult result = service.executeSupervisor(request,
                new AgentRuntimeSnapshot(20L, 21L, definition), mock(RuntimeCapabilityInvoker.class));

        assertEquals("是否批准读取完整账号信息？", result.content());
        verify(interactionSessionService).open(any());
        verify(eventPublisher).publishClarification(clarificationEvent);
    }

    private static ApplicationRun run(String executionId, RuntimeVariables variables, ChatTurnBO turn,
                                      ApplicationExecutionBudget budget) {
        return run(executionId, variables, turn, budget, ContextBudgetPolicy.defaults());
    }

    private static ApplicationRun run(String executionId, RuntimeVariables variables, ChatTurnBO turn,
                                      ApplicationExecutionBudget budget, ContextBudgetPolicy contextBudget) {
        AppRuntimeSnapshot application = mock(AppRuntimeSnapshot.class);
        ApplicationChatExecutionRequest request = ApplicationChatExecutionRequest.builder()
                .application(application)
                .query(variables.require(RuntimeVariableAccessors.USER_QUERY))
                .loginUser(new LoginUser(7L, "tester", "测试用户", null))
                .turn(turn)
                .eventPublisher(ChatExecutionEventPublisher.noop())
                .build();
        ConversationContext conversation = mock(ConversationContext.class);
        when(conversation.getRuntimeVariables()).thenReturn(variables);
        when(conversation.getBudget()).thenReturn(contextBudget);
        return new ApplicationRun(executionId, request, conversation, budget);
    }

    private static AgentExecutionAdapter service(AgentRuntimeDefinition definition, PromptRuntimeService promptRuntimeService,
                                                Function<ChatStreamCommand, Flux<ChatStreamChunk>> streamingChatGateway,
                                                ToolRuntimeService toolRuntimeService) {
        return service(definition, promptRuntimeService, streamingChatGateway, toolRuntimeService, null);
    }

    private static AgentExecutionAdapter service(AgentRuntimeDefinition definition, PromptRuntimeService promptRuntimeService,
                                                Function<ChatStreamCommand, Flux<ChatStreamChunk>> streamingChatGateway,
                                                ToolRuntimeService toolRuntimeService,
                                                AtomicReference<RuntimeContext> capturedRuntimeContext) {
        ModelRuntimeConfigService modelRuntimeConfigService = modelService();
        ExecutionPersistenceService persistenceService = testPersistenceService();
        return new AgentExecutionAdapter(
                agentVersionService(definition),
                runtime(streamingChatGateway, null, capturedRuntimeContext),
                promptRuntimeService,
                modelRequestFactory(),
                modelRuntimeConfigService,
                toolRuntimeService,
                mock(KnowledgeSearchAgentToolFactory.class),
                mock(com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore.class),
                emptySkillRuntimeService(),
                persistenceService,
                new ApplicationRuntimeEventSinkFactory(persistenceService,
                        io.micrometer.observation.ObservationRegistry.NOOP),
                mock(com.lingxi.workbench.application.runtime.service.execution.ApplicationRuntimeTaskStore.class),
                mock(com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService.class),
                new PromptTemplateRenderer(),
                mock(org.springframework.ai.tokenizer.TokenCountEstimator.class));
    }

    /** 让 Application 单元测试经过真实 AgentRuntime，同时以 AgentScope Gateway stub 提供模型分片。 */
    private static AgentRuntime runtime(Function<ChatStreamCommand, Flux<ChatStreamChunk>> streamingChatGateway) {
        return runtime(streamingChatGateway, null, null);
    }

    private static AgentRuntime runtime(Function<ChatStreamCommand, Flux<ChatStreamChunk>> streamingChatGateway,
                                        AtomicReference<ExecutionBudget> capturedBudget) {
        return runtime(streamingChatGateway, capturedBudget, null);
    }

    private static AgentRuntime runtime(Function<ChatStreamCommand, Flux<ChatStreamChunk>> streamingChatGateway,
                                        AtomicReference<ExecutionBudget> capturedBudget,
                                        AtomicReference<RuntimeContext> capturedRuntimeContext) {
        return runtime(streamingChatGateway, capturedBudget, capturedRuntimeContext, null);
    }

    private static AgentRuntime runtime(Function<ChatStreamCommand, Flux<ChatStreamChunk>> streamingChatGateway,
                                        AtomicReference<ExecutionBudget> capturedBudget,
                                        AtomicReference<RuntimeContext> capturedRuntimeContext,
                                        AtomicReference<AgentScopePublishedSkillCatalog> capturedSkillCatalog) {
        AgentScopeSpecialistStreamingGateway agentScopeGateway = mock(AgentScopeSpecialistStreamingGateway.class);
        org.mockito.stubbing.Answer<Flux<ChatStreamChunk>> answer = invocation -> Flux.defer(() -> {
            AgentRunRequest request = invocation.getArgument(0);
            RuntimeContext runtimeContext = invocation.getArgument(1);
            AgentScopePublishedSkillCatalog skillCatalog = invocation.getArgument(2);
            ExecutionBudget budget = runtimeContext.get(ExecutionBudget.class);
            ExecutionTools tools = runtimeContext.get(ExecutionTools.class);
            ExecutionEvents events = runtimeContext.get(ExecutionEvents.class);
            if (capturedBudget != null) {
                capturedBudget.set(budget);
            }
            if (capturedRuntimeContext != null) {
                capturedRuntimeContext.set(runtimeContext);
            }
            if (capturedSkillCatalog != null) {
                capturedSkillCatalog.set(skillCatalog);
            }
            int callNo = budget.reserveModelCall();
            tools.startModelRound();
            events.modelStarted(callNo, 9L, "test-model", false);
            AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.empty());
            return streamingChatGateway.apply(request.getModelCommand())
                    .doOnNext(chunk -> {
                        if (chunk.hasUsage()) {
                            usage.set(chunk.usage());
                        }
                    })
                    .doOnComplete(() -> {
                        budget.completeModelCall(usage.get());
                        events.modelCompleted(callNo, 9L, "test-model", usage.get(), false, null);
                    });
        });
        when(agentScopeGateway.stream(any(AgentRunRequest.class), any(), any()))
                .thenAnswer(answer);
        return new AgentRuntime(agentScopeGateway, mock(AgentScopeSupervisorGateway.class),
                mock(io.agentscope.core.state.AgentStateStore.class));
    }

    private static SkillRuntimeService emptySkillRuntimeService() {
        SkillRuntimeService service = mock(SkillRuntimeService.class);
        when(service.load(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(SkillRuntimeCatalog.empty());
        return service;
    }

    private static ExecutionPersistenceService testPersistenceService() {
        ExecutionPersistenceService service = mock(ExecutionPersistenceService.class);
        AtomicLong sequence = new AtomicLong();
        org.mockito.stubbing.Answer<ExecutionEventEntity> persistedEvent = invocation -> {
            ExecutionEventEntity event = new ExecutionEventEntity();
            event.setSequenceNo(sequence.incrementAndGet());
            return event;
        };
        when(service.appendEvent(any())).thenAnswer(persistedEvent);
        when(service.appendSkillActivationEvent(any(), any())).thenAnswer(persistedEvent);
        return service;
    }

    private static ModelRuntimeConfigService modelService() {
        return new ModelRuntimeConfigService() {
            @Override
            public ChatModelRuntimeConfig getDefaultChatModel() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChatModelRuntimeConfig getChatModelConfig(Long instanceId) {
                return ChatModelRuntimeConfig.builder()
                        .instanceId(instanceId)
                        .baseUrl("http://localhost")
                        .apiKey("secret")
                        .modelCode("chat")
                        .temperature(0.7)
                        .topP(1.0)
                        .maxTokens(4096)
                        .stopSequences(List.of())
                        .extensionParameters(JSONObject.of(
                                "thinking",
                                JSONObject.of("type", "disabled")))
                        .timeoutSeconds(120)
                        .maxRetries(0)
                        .build();
            }

            @Override
            public EmbeddingModelRuntimeConfig getEmbeddingModelConfig(
                    Long instanceId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static AgentRuntimeDefinition definition() {
        return AgentRuntimeDefinition.builder()
                .schemaVersion(AGENT_SCHEMA_VERSION)
                .role(AgentRole.SPECIALIST)
                .chatModelInstanceId(99L)
                .systemPromptVersionId(88L)
                .userPromptVersionId(89L)
                .toolCodes(List.of())
                .skills(List.of())
                .generationPolicy(AgentRuntimeDefinition.GenerationPolicy.builder()
                        .temperature(new BigDecimal("0.2"))
                        .topP(new BigDecimal("0.9"))
                        .build())
                .build();
    }

    private static AgentRuntimeDefinition reactDefinition() {
        return AgentRuntimeDefinition.builder()
                .schemaVersion(AGENT_SCHEMA_VERSION)
                .role(AgentRole.SPECIALIST)
                .chatModelInstanceId(99L)
                .systemPromptVersionId(88L)
                .userPromptVersionId(89L)
                .toolCodes(List.of())
                .skills(List.of())
                .toolPolicy(new AgentRuntimeDefinition.ToolPolicy(1000))
                .knowledgeSearch(AgentRuntimeDefinition.KnowledgeSearchCapability.builder()
                        .knowledgeBaseId(30L)
                        .knowledgeIndexVersionId(31L)
                        .embeddingModelInstanceId(40L)
                        .topK(5)
                        .similarityThreshold(new BigDecimal("0.65"))
                        .maxResultTokens(2000)
                        .maxQueryCharacters(1000)
                        .timeoutSeconds(8)
                        .build())
                .generationPolicy(AgentRuntimeDefinition.GenerationPolicy.builder()
                        .temperature(new BigDecimal("0.2"))
                        .topP(new BigDecimal("0.9"))
                        .build())
                .build();
    }

    private static LoginUser loginUser() {
        return new LoginUser(7L, "tester", "测试用户", null);
    }

    private static RuntimeVariables runtimeVariables() {
        return runtimeVariables("如何退款");
    }

    private static RuntimeVariables runtimeVariables(String query) {
        RuntimeVariables variables = new RuntimeVariablesInitializer("Lingxi Workbench")
                .initialize(loginUser(), query, 10L);
        variables.put(RuntimeVariableAccessors.SESSION_HISTORY, List.of(
                new ChatHistoryMessage(ChatHistoryMessage.Role.USER, "历史问题")));
        return variables;
    }

    private static PromptRuntimeSnapshot promptSnapshot() {
        return new PromptRuntimeSnapshot(
                88L,
                "你是售后助手",
                List.of(),
                PromptRole.SYSTEM);
    }

    private static PromptRuntimeSnapshot promptSnapshotByVersion(Long promptVersionId) {
        if (promptVersionId == 89L) {
            return new PromptRuntimeSnapshot(89L, "{{user.query}}", List.of(new PromptRuntimeVariableDefinition("user.query", "STRING", true)), PromptRole.USER);
        }
        return promptSnapshot();
    }

    private static PromptRuntimeSnapshot toolConflictPromptSnapshotByVersion(Long promptVersionId) {
        if (promptVersionId == 89L) {
            return promptSnapshotByVersion(promptVersionId);
        }
        return new PromptRuntimeSnapshot(promptVersionId, "只能依据知识片段回答，不得调用工具", List.of(), PromptRole.SYSTEM);
    }

    private static ModelRequestFactory modelRequestFactory() {
        return new ModelRequestFactory(new PromptTemplateRenderer());
    }

    private static final class StubAgentTool implements AgentTool {

        @Override
        public AgentToolDefinition definition() {
            return AgentToolDefinition.builder()
                    .code("system_current_time")
                    .displayName("查询当前时间")
                    .description("查询当前时间")
                    .build();
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public String execute(AgentToolRequest request) {
            return "{\"currentTime\":\"2026-07-31T10:00:00+08:00\"}";
        }
    }

    private static final class KnowledgeSearchStubAgentTool implements AgentTool {

        @Override
        public AgentToolDefinition definition() {
            return AgentToolDefinition.builder()
                    .code("knowledge_search")
                    .displayName("知识搜索")
                    .description("搜索发布知识")
                    .build();
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties",
                    Map.of("query", Map.of("type", "string")));
        }

        @Override
        public String execute(AgentToolRequest request) {
            return "{\"chunks\":[]}";
        }
    }
}
