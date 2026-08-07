package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.AgentCollaborationScope;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.application.runtime.service.ToolRuntimeService;
import com.lingxi.workbench.application.runtime.tool.KnowledgeSearchAgentToolFactory;
import com.lingxi.workbench.application.runtime.service.impl.strategy.RagChatPreparationStrategy;
import com.lingxi.workbench.application.runtime.service.impl.strategy.ReactPreparationStrategy;
import com.lingxi.workbench.application.runtime.service.impl.strategy.SupervisorPreparationStrategy;
import com.lingxi.workbench.chat.common.enums.RetrievalStatus;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.ChatProcessChain;
import com.lingxi.workbench.chat.service.ChatProcessNode;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.AgentCollaborationLimits;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatServiceImplTest {

    @Test
    void shouldRunLockedAgentVersionThroughExistingChatChain() {
        AgentRuntimeDefinition definition = definition();
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-1")
                .runtimeVariables(runtimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();

        AgentChatServiceImpl service = service(
                definition,
                AgentChatServiceImplTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(
                            new ChatStreamChunk("工作流"),
                            new ChatStreamChunk("回答"),
                            new ChatStreamChunk("", TokenUsage.of(12, 4, 16)));
                },
                toolCodes -> List.of());

        String answer = service.chat(10L, 20L, "如何退款", execution);

        assertEquals("工作流回答", answer);
        assertEquals("你是售后助手", captured.get().systemPrompt());
        assertEquals(0.2D, captured.get().modelConfig().getTemperature());
        assertEquals(2048, captured.get().modelConfig().getMaxTokens());
        assertEquals(
                "disabled",
                captured.get().modelConfig().getExtensionParameters()
                        .getJSONObject("thinking")
                        .getString("type"));
        assertEquals(16, execution.tokenUsage().totalTokens());
        assertFalse(captured.get().history().isEmpty());
    }

    @Test
    void shouldRejectAgentVersionWithoutKnowledgeBinding() {
        AgentRuntimeDefinition definition = definition();
        definition.setKnowledge(null);
        AgentChatServiceImpl service = service(
                definition,
                AgentChatServiceImplTest::promptSnapshotByVersion,
                command -> Flux.empty(),
                toolCodes -> List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.chat(
                        10L,
                        20L,
                        "如何退款",
                        ChatExecutionContext.builder()
                                .executionId("run-invalid")
                                .runtimeVariables(runtimeVariables())
                                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                                .build()));

        assertEquals("AgentVersion知识库配置不完整", exception.getMessage());
    }

    @Test
    void shouldRejectNodeQueryThatDoesNotMatchEntryQuery() {
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-query-mismatch")
                .runtimeVariables(runtimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();
        AgentChatServiceImpl service = service(
                definition(),
                promptVersionId -> promptSnapshot(),
                command -> Flux.empty(),
                toolCodes -> List.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.chat(
                        10L,
                        20L,
                        "不同的问题",
                        execution));

        assertEquals(
                "Agent查询与运行时变量作用域不一致",
                exception.getMessage());
    }

    @Test
    void shouldExposeApplicationMembersAsSupervisorTools() {
        AgentRuntimeDefinition definition = supervisorDefinition();
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        ApplicationAgentReference supervisor = new ApplicationAgentReference(10L, 20L, "协调Agent", "负责规划");
        ApplicationAgentReference member = new ApplicationAgentReference(30L, 40L, "售后专家", "处理退款与售后政策");
        AgentCollaborationDefinition collaboration = new AgentCollaborationDefinition(true, supervisor, List.of(member), new AgentCollaborationLimits(2, 1));
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-collaboration")
                .runtimeVariables(runtimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .collaborationScope(AgentCollaborationScope.root(collaboration))
                .build();
        AgentChatServiceImpl service = supervisorService(
                definition,
                AgentChatServiceImplTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("统一回答"));
                },
                toolCodes -> List.of());

        String answer = service.chat(10L, 20L, "如何退款", execution);

        assertEquals("统一回答", answer);
        assertEquals(
                2,
                execution.getRuntimeVariables().require(
                        RuntimeVariableAccessors.COLLABORATION_MAX_DELEGATIONS));
        AgentTool delegationTool = captured.get().toolRuntimeConfig().orElseThrow().getTools().getFirst();
        assertEquals("delegate_agent_30", delegationTool.definition().getCode());
        assertTrue(delegationTool.definition().getDescription().contains("处理退款与售后政策"));
        assertTrue(captured.get().systemPrompt().contains("多 Agent 协作规则"));
        assertTrue(captured.get().systemPrompt().contains("成员返回的是中间工作结果"));
    }

    @Test
    void shouldRejectSupervisorSnapshotContainingRetrievalConfiguration() {
        AgentRuntimeDefinition definition = supervisorDefinition();
        definition.setRetrievalPolicy(definition().getRetrievalPolicy());
        AgentChatServiceImpl service = supervisorService(definition, AgentChatServiceImplTest::promptSnapshotByVersion, command -> Flux.empty(), toolCodes -> List.of());
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-invalid-supervisor")
                .runtimeVariables(runtimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.chat(10L, 20L, "如何退款", execution));

        assertEquals("Supervisor AgentVersion不能包含知识库或检索配置", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidToolLimitsBeforeCallingGateway() {
        AgentRuntimeDefinition definition = definition();
        definition.setToolCodes(List.of("system_current_time"));
        definition.setToolPolicy(new AgentRuntimeDefinition.ToolPolicy(0, 1000));
        AgentChatServiceImpl service = service(
                definition,
                promptVersionId -> promptSnapshot(),
                command -> {
                    throw new AssertionError("非法工具配置不应进入模型网关");
                },
                toolCodes -> List.of());
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-invalid-tool-limit")
                .runtimeVariables(runtimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.chat(10L, 20L, "如何退款", execution));

        assertEquals("AgentVersion工具调用配置超出允许范围", exception.getMessage());
    }

    @Test
    void shouldAppendToolEvidencePolicyWhenAgentEnablesTools() {
        AgentRuntimeDefinition definition = definition();
        definition.setToolCodes(List.of("system_current_time"));
        definition.setToolPolicy(new AgentRuntimeDefinition.ToolPolicy(1, 1000));
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentChatServiceImpl service = service(
                definition,
                promptVersionId -> new PromptRuntimeSnapshot(promptVersionId, "只能依据知识片段回答，不得调用工具", List.of(), PromptRole.SYSTEM),
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("现在是10点"));
                },
                toolCodes -> List.of(new StubAgentTool()));
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-with-tool")
                .runtimeVariables(runtimeVariables("现在是几点"))
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();

        String answer = service.chat(10L, 20L, "现在是几点", execution);

        assertEquals("现在是10点", answer);
        assertTrue(captured.get().toolRuntimeConfig().isPresent());
        assertTrue(captured.get().systemPrompt().indexOf("平台工具调用规则") > captured.get().systemPrompt().indexOf("不得调用工具"));
        assertTrue(captured.get().systemPrompt().contains("只能依据知识片段或系统授权工具的执行结果"));
        assertTrue(captured.get().systemPrompt().contains("在因为知识片段不足而拒绝回答之前"));
        assertTrue(captured.get().systemPrompt().contains("必须调用该工具"));
        assertTrue(captured.get().systemPrompt().contains("不得使用模型记忆、心算或猜测替代工具执行"));
        assertTrue(captured.get().systemPrompt().contains("工具执行结果是本轮回答可使用的可信事实来源"));
        assertTrue(captured.get().systemPrompt().contains("不得因为知识片段缺少相关内容而拒绝回答"));
    }

    @Test
    void shouldInjectFrozenKnowledgeSearchToolForReactAgent() {
        AgentRuntimeDefinition definition = reactDefinition();
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        KnowledgeSearchAgentToolFactory toolFactory = mock(KnowledgeSearchAgentToolFactory.class);
        when(toolFactory.create(definition.getKnowledgeSearch())).thenReturn(new KnowledgeSearchStubAgentTool());
        PromptRuntimeService promptRuntimeService = AgentChatServiceImplTest::promptSnapshotByVersion;
        ReactPreparationStrategy strategy = new ReactPreparationStrategy(promptRuntimeService, modelRequestFactory());
        AgentChatPreparationStrategyRegistry registry = new AgentChatPreparationStrategyRegistry(List.of(strategy));
        AgentChatServiceImpl service = new AgentChatServiceImpl(
                (agentId, versionId) -> new AgentRuntimeSnapshot(agentId, versionId, definition),
                registry,
                modelService(),
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("知识回答"));
                },
                toolCodes -> List.of(),
                toolFactory);
        RuntimeVariables variables = runtimeVariables("退款条件");
        variables.put(RuntimeVariableAccessors.SESSION_HISTORY, List.of());
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-react-knowledge")
                .runtimeVariables(variables)
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .build();

        String answer = service.chat(10L, 20L, "退款条件", execution);

        assertEquals("知识回答", answer);
        assertEquals("knowledge_search", captured.get().toolRuntimeConfig().orElseThrow().getTools().getFirst().definition().getCode());
        verify(toolFactory).create(definition.getKnowledgeSearch());
    }

    private static AgentChatServiceImpl service(AgentRuntimeDefinition definition, PromptRuntimeService promptRuntimeService, StreamingChatGateway streamingChatGateway, ToolRuntimeService toolRuntimeService) {
        ModelRuntimeConfigService modelRuntimeConfigService = modelService();
        RagChatPreparationStrategy strategy = new RagChatPreparationStrategy(promptRuntimeService, modelRuntimeConfigService, modelRequestFactory(), new ChatProcessChain(new PreparingNode()));
        AgentChatPreparationStrategyRegistry registry = new AgentChatPreparationStrategyRegistry(List.of(strategy));
        return new AgentChatServiceImpl((agentId, versionId) -> new AgentRuntimeSnapshot(agentId, versionId, definition), registry, modelRuntimeConfigService, streamingChatGateway, toolRuntimeService, mock(KnowledgeSearchAgentToolFactory.class));
    }

    private static AgentChatServiceImpl supervisorService(AgentRuntimeDefinition definition, PromptRuntimeService promptRuntimeService, StreamingChatGateway streamingChatGateway, ToolRuntimeService toolRuntimeService) {
        ModelRuntimeConfigService modelRuntimeConfigService = modelService();
        SupervisorPreparationStrategy strategy = new SupervisorPreparationStrategy(promptRuntimeService, modelRequestFactory(), new ChatProcessChain(new SupervisorPreparingNode()));
        AgentChatPreparationStrategyRegistry registry = new AgentChatPreparationStrategyRegistry(List.of(strategy));
        return new AgentChatServiceImpl((agentId, versionId) -> new AgentRuntimeSnapshot(agentId, versionId, definition), registry, modelRuntimeConfigService, streamingChatGateway, toolRuntimeService, mock(KnowledgeSearchAgentToolFactory.class));
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
                .schemaVersion(1)
                .agentType("RAG_CHAT")
                .chatModelInstanceId(99L)
                .answerPromptVersionId(88L)
                .knowledge(new AgentRuntimeDefinition.KnowledgeBinding(
                        30L, 40L, "LATEST", 0))
                .memoryPolicy(new AgentRuntimeDefinition.MemoryPolicy(
                        true, 10, 4000))
                .rewritePolicy(AgentRuntimeDefinition.RewritePolicy.builder()
                        .enabled(false)
                        .maxHistoryRounds(6)
                        .maxHistoryTokens(2000)
                        .temperature(new BigDecimal("0.1"))
                        .maxOutputTokens(128)
                        .maxOutputCharacters(500)
                        .timeoutMillis(3000)
                        .fallbackToOriginal(true)
                        .build())
                .retrievalPolicy(AgentRuntimeDefinition.RetrievalPolicy.builder()
                        .topK(5)
                        .similarityThreshold(new BigDecimal("0.65"))
                        .maxContextTokens(4000)
                        .emptyResultStrategy("ANSWER_WITHOUT_CONTEXT")
                        .failureStrategy("FAIL_REQUEST")
                        .build())
                .generationPolicy(AgentRuntimeDefinition.GenerationPolicy.builder()
                        .temperature(new BigDecimal("0.2"))
                        .topP(new BigDecimal("0.9"))
                        .maxOutputTokens(2048)
                        .timeoutSeconds(60)
                        .maxRetries(1)
                        .build())
                .runtimePolicy(new AgentRuntimeDefinition.RuntimePolicy(
                        90, 3, 12000, 2048))
                .build();
    }

    private static AgentRuntimeDefinition supervisorDefinition() {
        return AgentRuntimeDefinition.builder()
                .schemaVersion(3)
                .agentType("SUPERVISOR")
                .chatModelInstanceId(99L)
                .systemPromptVersionId(88L)
                .userPromptVersionId(89L)
                .memoryPolicy(new AgentRuntimeDefinition.MemoryPolicy(true, 10, 4000))
                .rewritePolicy(AgentRuntimeDefinition.RewritePolicy.builder().enabled(false).build())
                .generationPolicy(AgentRuntimeDefinition.GenerationPolicy.builder().temperature(new BigDecimal("0.2")).topP(new BigDecimal("0.9")).maxOutputTokens(2048).timeoutSeconds(60).maxRetries(1).build())
                .runtimePolicy(new AgentRuntimeDefinition.RuntimePolicy(90, 3, 12000, 2048))
                .build();
    }

    private static AgentRuntimeDefinition reactDefinition() {
        return AgentRuntimeDefinition.builder()
                .schemaVersion(4)
                .agentType("REACT")
                .chatModelInstanceId(99L)
                .systemPromptVersionId(88L)
                .userPromptVersionId(89L)
                .toolCodes(List.of())
                .toolPolicy(new AgentRuntimeDefinition.ToolPolicy(1, 1000))
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
                .memoryPolicy(new AgentRuntimeDefinition.MemoryPolicy(true, 10, 4000))
                .rewritePolicy(AgentRuntimeDefinition.RewritePolicy.builder().enabled(false).build())
                .generationPolicy(AgentRuntimeDefinition.GenerationPolicy.builder()
                        .temperature(new BigDecimal("0.2"))
                        .topP(new BigDecimal("0.9"))
                        .maxOutputTokens(2048)
                        .timeoutSeconds(60)
                        .maxRetries(1)
                        .build())
                .runtimePolicy(new AgentRuntimeDefinition.RuntimePolicy(90, 2, 12000, 2048))
                .build();
    }

    private static LoginUser loginUser() {
        return new LoginUser(7L, "tester", "测试用户", null);
    }

    private static RuntimeVariables runtimeVariables() {
        return runtimeVariables("如何退款");
    }

    private static RuntimeVariables runtimeVariables(String query) {
        return new RuntimeVariablesInitializer("Lingxi Workbench")
                .initialize(loginUser(), query, 10L);
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

    private static ModelRequestFactory modelRequestFactory() {
        return new ModelRequestFactory(context -> new PromptPair(context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY), context.getSystemPromptSnapshot().content()), new PromptTemplateRenderer());
    }

    private static final class PreparingNode implements ChatProcessNode {

        @Override
        public ChatProcessNode next(ChatProcessNode next) {
            return next;
        }

        @Override
        public void process(ModelChatContext context) {
            assertEquals(30L, context.getKnowledgeBaseId());
            assertEquals(40L, context.getEmbeddingModelInstanceId());
            assertEquals(5, context.getRetrievalTopK());
            assertEquals("FAIL_REQUEST", context.getRetrievalFailureStrategy());
            context.setRetrievalStatus(RetrievalStatus.SUCCESS);
            context.getRuntimeVariables().put(
                    RuntimeVariableAccessors.SESSION_HISTORY,
                    List.of(
                            new ChatHistoryMessage(ChatHistoryMessage.Role.USER, "历史问题")));
        }
    }

    /** 模拟 Supervisor 的历史与记忆投影节点，断言运行时未注入 RAG 配置。 */
    private static final class SupervisorPreparingNode implements ChatProcessNode {

        @Override
        public ChatProcessNode next(ChatProcessNode next) {
            return next;
        }

        @Override
        public void process(ModelChatContext context) {
            assertEquals(null, context.getKnowledgeBaseId());
            assertEquals(null, context.getRetrievalTopK());
            context.getRuntimeVariables().put(RuntimeVariableAccessors.SESSION_HISTORY, List.of());
        }
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
        public ToolCallback callback() {
            return new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return ToolDefinition.builder()
                            .name("system_current_time")
                            .description("查询当前时间")
                            .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                            .build();
                }

                @Override
                public ToolMetadata getToolMetadata() {
                    return ToolMetadata.builder().build();
                }

                @Override
                public String call(String toolInput) {
                    return "{\"currentTime\":\"2026-07-31T10:00:00+08:00\"}";
                }
            };
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
        public ToolCallback callback() {
            return new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return ToolDefinition.builder()
                            .name("knowledge_search")
                            .description("搜索发布知识")
                            .inputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")
                            .build();
                }

                @Override
                public String call(String toolInput) {
                    return "{\"chunks\":[]}";
                }
            };
        }
    }
}
