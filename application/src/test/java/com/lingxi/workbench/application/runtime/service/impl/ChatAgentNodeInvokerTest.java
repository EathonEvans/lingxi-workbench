package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatWorkflowExecutionContext;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.ChatWorkflowExecutionRegistry;
import com.lingxi.workbench.chat.common.enums.RetrievalStatus;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.PromptPair;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.ChatProcessChain;
import com.lingxi.workbench.chat.service.ChatProcessNode;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.AgentNodeInvocation;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatAgentNodeInvokerTest {

    @Test
    void shouldRunLockedAgentVersionThroughExistingChatChain() {
        AgentRuntimeDefinition definition = definition();
        ChatWorkflowExecutionRegistry registry = new ChatWorkflowExecutionRegistry();
        ChatWorkflowExecutionContext execution = new ChatWorkflowExecutionContext(
                runtimeVariables(), new ChatTurnBO(10L, 11L, 12L, 3));
        registry.register("run-1", execution);
        AtomicReference<ChatStreamCommand> captured = new AtomicReference<>();

        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(
                (agentId, versionId) ->
                        new AgentRuntimeSnapshot(agentId, versionId, definition),
                promptVersionId -> promptSnapshot(),
                registry,
                modelService(),
                new ChatProcessChain(new PreparingNode()),
                command -> {
                    captured.set(command);
                    return Flux.just(
                            new ChatStreamChunk("工作流"),
                            new ChatStreamChunk("回答"),
                            new ChatStreamChunk("", 12, 4, 16));
                });

        Map<String, Object> outputs = invoker.invoke(AgentNodeInvocation.builder()
                .executionId("run-1")
                .applicationVersionId("30")
                .nodeId("agent-node")
                .agentId(10L)
                .agentVersionId(20L)
                .inputs(Map.of("query", "如何退款"))
                .build());

        assertEquals("工作流回答", outputs.get("answer"));
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
        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(
                (agentId, versionId) ->
                        new AgentRuntimeSnapshot(agentId, versionId, definition),
                promptVersionId -> promptSnapshot(),
                new ChatWorkflowExecutionRegistry(),
                modelService(),
                new ChatProcessChain(new PreparingNode()),
                command -> Flux.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoker.invoke(AgentNodeInvocation.builder()
                        .executionId("run-invalid")
                        .applicationVersionId("30")
                        .nodeId("agent-node")
                        .agentId(10L)
                        .agentVersionId(20L)
                        .inputs(Map.of("query", "如何退款"))
                        .build()));

        assertEquals("AgentVersion知识库配置不完整", exception.getMessage());
    }

    @Test
    void shouldRejectNodeQueryThatDoesNotMatchEntryQuery() {
        ChatWorkflowExecutionRegistry registry =
                new ChatWorkflowExecutionRegistry();
        registry.register(
                "run-query-mismatch",
                new ChatWorkflowExecutionContext(
                        runtimeVariables(),
                        new ChatTurnBO(10L, 11L, 12L, 3)));
        ChatAgentNodeInvoker invoker = new ChatAgentNodeInvoker(
                (agentId, versionId) ->
                        new AgentRuntimeSnapshot(
                                agentId,
                                versionId,
                                definition()),
                promptVersionId -> promptSnapshot(),
                registry,
                modelService(),
                new ChatProcessChain(new PreparingNode()),
                command -> Flux.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invoker.invoke(AgentNodeInvocation.builder()
                        .executionId("run-query-mismatch")
                        .applicationVersionId("30")
                        .nodeId("agent-node")
                        .agentId(10L)
                        .agentVersionId(20L)
                        .inputs(Map.of("query", "不同的问题"))
                        .build()));

        assertEquals(
                "当前阶段仅支持入口Agent使用用户原始query",
                exception.getMessage());
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
            public EmbeddingModelRuntimeConfig getEmbeddingModelConfig(Long instanceId) {
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

    private static LoginUser loginUser() {
        return new LoginUser(7L, "tester", "测试用户", null);
    }

    private static RuntimeVariables runtimeVariables() {
        return new RuntimeVariablesInitializer("Lingxi Workbench")
                .initialize(loginUser(), "如何退款", 10L);
    }

    private static PromptRuntimeSnapshot promptSnapshot() {
        return new PromptRuntimeSnapshot(
                88L,
                "你是售后助手",
                List.of(),
                PromptRole.SYSTEM);
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
                    new com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage(
                            com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage.Role.USER,
                            "历史问题")));
            context.setPromptPair(new PromptPair(
                    context.getRuntimeVariables().require(
                            RuntimeVariableAccessors.USER_QUERY),
                    context.getSystemPrompt().content()));
        }
    }
}
