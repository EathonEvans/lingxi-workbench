package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapability;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityResult;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityRisk;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityType;
import com.lingxi.workbench.agentruntime.plan.RuntimeTaskStore;
import com.lingxi.workbench.agentruntime.plan.SupervisorRunOutcome;
import com.lingxi.workbench.agentruntime.plan.SupervisorRunRequest;
import com.lingxi.workbench.agentruntime.plan.SupervisorRunResult;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeChatModelResource;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeOpenAiChatModelFactory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.core.state.Task;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScopeSupervisorRuntimeTest {

    @Test
    void shouldPersistAgentScopeTasksAndExecutePublishedCapability() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        StubChatModel model = StubChatModel.sequence(List.of(
                List.of(new ToolUseBlock("todo-1", "todo_write", Map.of("todos", List.of(
                        Map.of("content", "查询已发布资料", "status", "in_progress", "priority", "high",
                                "capabilityCode", "research"))),
                        "{\"todos\":[{\"content\":\"查询已发布资料\",\"status\":\"in_progress\",\"priority\":\"high\",\"capabilityCode\":\"research\"}]}", null)),
                List.of(new ToolUseBlock("research-1", "research", Map.of("query", "查询资料"),
                        "{\"query\":\"查询资料\"}", null)),
                List.of(TextBlock.builder().text("资料查询完成").build())));
        AgentScopeOpenAiChatModelFactory modelFactory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);
        when(tokenCountEstimator.estimate(org.mockito.ArgumentMatchers.any(String.class))).thenReturn(10);
        RecordingStore store = new RecordingStore();
        RecordingSink sink = new RecordingSink();
        AtomicInteger invocations = new AtomicInteger();
        AgentScopeSupervisorRuntime runtime = new AgentScopeSupervisorRuntime(modelFactory, tokenCountEstimator);

        SupervisorRunResult result = runtime.run(SupervisorRunRequest.builder()
                .runId("run-task-1").userId("1").agentId(10L).agentVersionId(20L)
                .systemPrompt("按平台规则处理任务。").query("查询资料")
                .modelConfig(config).planPolicy(PlanPolicy.builder().maxSteps(3).maxReplans(0)
                        .maxProtocolRepairAttempts(0).build())
                .executionPolicy(ExecutionPolicy.builder().maxModelCalls(3).maxToolCalls(2)
                        .maxTotalTokens(200).maxFinalizationTokens(200).build())
                .maxDelegations(2).capabilities(List.of(RuntimeCapability.builder()
                        .code("research").name("资料检索").type(RuntimeCapabilityType.AGENT)
                        .risk(RuntimeCapabilityRisk.READ_ONLY).maxCalls(1).resultTokenBudget(100).build()))
                .capabilityInvoker(invocation -> {
                    invocations.incrementAndGet();
                    invocation.recordExecution(new com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityExecutionFacts(
                            0, 0, com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage.empty(), 0));
                    return RuntimeCapabilityResult.builder().content("检索到已发布资料").build();
                })
                .taskStore(store).eventSink(sink).initialSequenceNo(0)
                .agentStateStore(new InMemoryAgentStateStore()).build());

        assertEquals(SupervisorRunOutcome.COMPLETED, result.getOutcome());
        assertEquals("资料查询完成", result.getContent());
        assertEquals(1, store.tasks.size());
        assertEquals("查询已发布资料", store.tasks.getFirst().getFirst().getSubject());
        assertEquals("research", store.tasks.getFirst().getFirst().getMetadata().get("capabilityCode"));
        assertEquals(1, invocations.get());
        assertEquals(1, store.invocations.size());
        assertTrue(sink.types().containsAll(List.of(ExecutionEventType.PLANNING,
                ExecutionEventType.PLAN_CREATED, ExecutionEventType.STEP_STARTED,
                ExecutionEventType.TOOL_STARTED, ExecutionEventType.TOOL_COMPLETED,
                ExecutionEventType.STEP_COMPLETED, ExecutionEventType.RUN_COMPLETED)));
    }

    @Test
    void shouldExposeAgentScopeClarificationAsRecoverableOutcome() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        StubChatModel model = StubChatModel.sequence(List.of(
                List.of(new ToolUseBlock("clarification-1", "request_clarification", Map.of(
                        "question", "请补充需要查询的资料范围", "options", List.of("产品规则", "订单记录")),
                        "{\"question\":\"请补充需要查询的资料范围\",\"options\":[\"产品规则\",\"订单记录\"]}", null)),
                List.of(TextBlock.builder().text("等待用户补充").build())));
        AgentScopeOpenAiChatModelFactory modelFactory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        AgentScopeSupervisorRuntime runtime = new AgentScopeSupervisorRuntime(modelFactory,
                mock(TokenCountEstimator.class));
        RecordingStore store = new RecordingStore();
        RecordingSink sink = new RecordingSink();

        SupervisorRunResult result = runtime.run(SupervisorRunRequest.builder()
                .runId("run-clarification-1").userId("1").agentId(10L).agentVersionId(20L)
                .systemPrompt("按平台规则处理任务。")
                .query("帮我查询资料")
                .modelConfig(config).planPolicy(PlanPolicy.builder().maxSteps(3).maxReplans(0)
                        .maxProtocolRepairAttempts(0).build())
                .executionPolicy(ExecutionPolicy.builder().maxModelCalls(2).maxToolCalls(1)
                        .maxTotalTokens(200).maxFinalizationTokens(200).build())
                .maxDelegations(0).capabilities(List.of())
                .capabilityInvoker(invocation -> {
                    throw new AssertionError("澄清不应调用能力");
                })
                .taskStore(store).eventSink(sink).initialSequenceNo(0)
                .agentStateStore(new InMemoryAgentStateStore()).build());

        assertEquals(SupervisorRunOutcome.CLARIFICATION, result.getOutcome());
        assertEquals("请补充需要查询的资料范围", result.getClarification().getQuestion());
        assertEquals(List.of("产品规则", "订单记录"), result.getClarification().options());
        assertTrue(sink.types().contains(ExecutionEventType.CLARIFICATION_REQUIRED));
        assertTrue(store.tasks.isEmpty());
        assertFalse(store.invocations.size() > 0);
    }

    private static final class StubChatModel extends ChatModelBase {

        private final List<List<ContentBlock>> responses;
        private final AtomicInteger responseNo = new AtomicInteger();

        private StubChatModel(List<List<ContentBlock>> responses) {
            this.responses = responses;
        }

        private static StubChatModel sequence(List<List<ContentBlock>> responses) {
            return new StubChatModel(responses);
        }

        @Override
        public String getModelName() {
            return "supervisor-stub";
        }

        @Override
        protected Flux<ChatResponse> doStream(List<Msg> messages, List<ToolSchema> tools,
                                              GenerateOptions options) {
            List<ContentBlock> content = responses.get(Math.min(responseNo.getAndIncrement(), responses.size() - 1));
            return Flux.just(ChatResponse.builder().content(content)
                    .usage(ChatUsage.builder().inputTokens(5).outputTokens(2).build())
                    .finishReason(content.stream().anyMatch(ToolUseBlock.class::isInstance) ? "tool_calls" : "stop")
                    .build());
        }

    }

    private static final class RecordingStore implements RuntimeTaskStore {

        private final List<List<Task>> tasks = new ArrayList<>();
        private final List<AgentInvocationState> invocations = new ArrayList<>();

        @Override
        public void saveTasks(String runId, int revision, List<Task> taskSnapshot) {
            tasks.add(List.copyOf(taskSnapshot));
        }

        @Override
        public void saveInvocation(AgentInvocationState invocation) {
            invocations.add(invocation);
        }
    }

    private static final class InMemoryAgentStateStore implements AgentStateStore {

        private final Map<String, State> states = new ConcurrentHashMap<>();

        @Override
        public void save(String userId, String sessionId, String key, State value) {
            states.put(storageKey(userId, sessionId, key), value);
        }

        @Override
        public void save(String userId, String sessionId, String key, List<? extends State> values) {
            // 当前 Supervisor 只使用 AgentState 单值；列表状态由 AgentScope 默认恢复逻辑忽略。
        }

        @Override
        public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
            State state = states.get(storageKey(userId, sessionId, key));
            return state != null && type.isInstance(state) ? Optional.of(type.cast(state)) : Optional.empty();
        }

        @Override
        public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
            return List.of();
        }

        @Override
        public boolean exists(String userId, String sessionId) {
            return states.keySet().stream().anyMatch(key -> key.startsWith(storageKey(userId, sessionId, "")));
        }

        @Override
        public void delete(String userId, String sessionId) {
            states.keySet().removeIf(key -> key.startsWith(storageKey(userId, sessionId, "")));
        }

        @Override
        public Set<String> listSessionIds(String userId) {
            return Set.of();
        }

        private static String storageKey(String userId, String sessionId, String key) {
            return userId + ":" + sessionId + ":" + key;
        }
    }

    private static final class RecordingSink implements RuntimeEventSink {

        private final List<ExecutionEvent> events = new ArrayList<>();

        @Override
        public void onEvent(ExecutionEvent event) {
            events.add(event);
        }

        @Override
        public void onText(ChatStreamChunk chunk) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        private List<ExecutionEventType> types() {
            return events.stream().map(ExecutionEvent::getType).toList();
        }
    }
}
