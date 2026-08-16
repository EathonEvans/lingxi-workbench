package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.application.runtime.service.tool.ToolRuntimeService;
import com.lingxi.workbench.application.runtime.tool.KnowledgeSearchAgentToolFactory;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION;
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
                .contextBudget(ContextBudgetPolicy.builder().reservedOutputTokens(2048).build())
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
    void shouldRejectAgentVersionWithoutUserPrompt() {
        AgentRuntimeDefinition definition = definition();
        definition.setUserPromptVersionId(null);
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

        assertEquals("AgentVersion缺少模型、SYSTEM Prompt或USER Prompt", exception.getMessage());
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
    void shouldDisableToolsAndFinalizeWhenApplicationToolBudgetIsUnavailable() {
        AgentRuntimeDefinition definition = definition();
        definition.setToolCodes(List.of("system_current_time"));
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentChatServiceImpl service = service(
                definition,
                AgentChatServiceImplTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("已基于现有上下文回答"));
                },
                toolCodes -> List.of(new StubAgentTool()));
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-invalid-tool-limit")
                .runtimeVariables(runtimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .runtimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(3)
                        .maxToolCalls(0)
                        .maxDelegations(0)
                        .totalTokenBudget(8000)
                        .timeoutSeconds(120)
                        .build())
                .build();

        String answer = service.chat(10L, 20L, "如何退款", execution);

        assertEquals("已基于现有上下文回答", answer);
        assertTrue(captured.get().toolRuntimeConfig().isEmpty());
        assertTrue(captured.get().systemPrompt().contains("本次不再开放工具、委派或规划"));
    }

    @Test
    void shouldAppendToolEvidencePolicyWhenAgentEnablesTools() {
        AgentRuntimeDefinition definition = definition();
        definition.setToolCodes(List.of("system_current_time"));
        definition.setToolPolicy(new AgentRuntimeDefinition.ToolPolicy(1000));
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentChatServiceImpl service = service(
                definition,
                AgentChatServiceImplTest::toolConflictPromptSnapshotByVersion,
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
    void shouldKeepToolBudgetIndependentFromRemainingModelCalls() {
        AgentRuntimeDefinition definition = definition();
        definition.setToolCodes(List.of("system_current_time"));
        definition.setToolPolicy(new AgentRuntimeDefinition.ToolPolicy(1000));
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentChatServiceImpl service = service(
                definition,
                AgentChatServiceImplTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("已有答案"));
                },
                toolCodes -> List.of(new StubAgentTool()));
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-independent-budgets")
                .runtimeVariables(runtimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .runtimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(3)
                        .maxDelegations(0)
                        .totalTokenBudget(8000)
                        .timeoutSeconds(120)
                        .build())
                .build();

        service.chat(10L, 20L, "如何退款", execution);

        assertEquals(3, captured.get().toolRuntimeConfig().orElseThrow().getMaxToolCalls());
        assertEquals(2, captured.get().toolRuntimeConfig().orElseThrow().getMaxModelCalls());
    }

    @Test
    void shouldClampModelTimeoutToApplicationDeadlineIncludingActiveOutputGrace() {
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        AgentChatServiceImpl service = service(
                definition(),
                AgentChatServiceImplTest::promptSnapshotByVersion,
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("按时回答"));
                },
                toolCodes -> List.of());
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("run-timeout")
                .runtimeVariables(runtimeVariables())
                .turn(new ChatTurnBO(10L, 11L, 12L, 3))
                .runtimePolicy(ApplicationRuntimePolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(0)
                        .maxDelegations(0)
                        .totalTokenBudget(8000)
                        .timeoutSeconds(30)
                        .build())
                .build();

        service.chat(10L, 20L, "如何退款", execution);

        assertEquals(33, captured.get().modelConfig().getTimeoutSeconds());
    }

    @Test
    void shouldInjectFrozenKnowledgeSearchToolForReactAgent() {
        AgentRuntimeDefinition definition = reactDefinition();
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();
        KnowledgeSearchAgentToolFactory toolFactory = mock(KnowledgeSearchAgentToolFactory.class);
        when(toolFactory.create(definition.getKnowledgeSearch())).thenReturn(new KnowledgeSearchStubAgentTool());
        PromptRuntimeService promptRuntimeService = AgentChatServiceImplTest::promptSnapshotByVersion;
        AgentChatServiceImpl service = new AgentChatServiceImpl(
                (agentId, versionId) -> new AgentRuntimeSnapshot(agentId, versionId, definition),
                promptRuntimeService,
                modelRequestFactory(),
                modelService(),
                command -> {
                    captured.set(command);
                    return Flux.just(new ChatStreamChunk("知识回答"));
                },
                toolCodes -> List.of(),
                toolFactory,
                mock(com.lingxi.workbench.application.runtime.service.interaction.ToolApprovalGateFactory.class));
        RuntimeVariables variables = runtimeVariables("退款条件");
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
        return new AgentChatServiceImpl(
                (agentId, versionId) -> new AgentRuntimeSnapshot(agentId, versionId, definition),
                promptRuntimeService,
                modelRequestFactory(),
                modelRuntimeConfigService,
                streamingChatGateway,
                toolRuntimeService,
                mock(KnowledgeSearchAgentToolFactory.class),
                mock(com.lingxi.workbench.application.runtime.service.interaction.ToolApprovalGateFactory.class));
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
