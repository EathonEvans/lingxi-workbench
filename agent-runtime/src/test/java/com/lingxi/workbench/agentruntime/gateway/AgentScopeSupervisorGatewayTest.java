package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.AgentRuntime;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.AgentRunResult;
import com.lingxi.workbench.agentruntime.plan.AgentInvocationState;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapability;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityException;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityResult;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityRisk;
import com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityType;
import com.lingxi.workbench.agentruntime.plan.RuntimeTaskStore;
import com.lingxi.workbench.agentruntime.plan.SupervisorRuntimeConfig;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeChatModelResource;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeOpenAiChatModelFactory;
import com.lingxi.workbench.infra.ai.token.TextTokenEstimator;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScopeSupervisorGatewayTest {

    @Test
    void shouldPersistAgentScopeTasksAndExecutePublishedCapability() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        StubChatModel model = StubChatModel.sequence(List.of(
                List.of(new ToolUseBlock("todo-1", "todo_write", Map.of("todos", List.of(
                        Map.of("content", "查询已发布资料", "status", "in_progress", "priority", "high"))),
                        "{\"todos\":[{\"content\":\"查询已发布资料\",\"status\":\"in_progress\",\"priority\":\"high\"}]}", null)),
                List.of(new ToolUseBlock("delegate-1", "agent_spawn",
                        Map.of("agent_id", "research", "task", "查询资料", "timeout_seconds", 0),
                        "{\"agent_id\":\"research\",\"task\":\"查询资料\",\"timeout_seconds\":0}", null)),
                List.of(new ToolUseBlock("wait-1", "wait_async_results",
                        Map.of("timeout_seconds", 1, "wait_all", true),
                        "{\"timeout_seconds\":1,\"wait_all\":true}", null)),
                List.of(new ToolUseBlock("todo-2", "todo_write", Map.of("todos", List.of(
                        Map.of("content", "查询已发布资料", "status", "completed", "priority", "high"))),
                        "{\"todos\":[{\"content\":\"查询已发布资料\",\"status\":\"completed\",\"priority\":\"high\"}]}", null)),
                List.of(TextBlock.builder().text("资料查询完成").build())));
        AgentScopeOpenAiChatModelFactory modelFactory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        TextTokenEstimator tokenCountEstimator = mock(TextTokenEstimator.class);
        when(tokenCountEstimator.estimate(org.mockito.ArgumentMatchers.any(String.class))).thenReturn(10);
        RecordingStore store = new RecordingStore();
        RecordingSink sink = new RecordingSink();
        AtomicInteger invocations = new AtomicInteger();
        AgentRuntime runtime = runtime(modelFactory, tokenCountEstimator);

        AgentRunResult result = runtime.run(AgentRunRequest.builder()
                .runId("run-task-1").userId("1").sessionId("run-task-1")
                .agentId(10L).agentVersionId(20L).role(AgentRole.SUPERVISOR)
                .modelCommand(command("按平台规则处理任务。", "查询资料", config))
                .policy(ExecutionPolicy.builder().maxModelCalls(5).maxToolCalls(3)
                        .maxTotalTokens(200).maxFinalizationTokens(200).build())
                .eventSink(sink).initialSequenceNo(0).initialUsage(TokenUsage.empty())
                .supervisorConfig(SupervisorRuntimeConfig.builder()
                        .planPolicy(PlanPolicy.builder().maxSteps(3).maxReplans(0)
                                .maxProtocolRepairAttempts(0).build())
                        .maxDelegations(2).capabilities(List.of(RuntimeCapability.builder()
                        .code("research").name("资料检索").type(RuntimeCapabilityType.AGENT).targetId(30L)
                        .risk(RuntimeCapabilityRisk.READ_ONLY).maxCalls(1).resultTokenBudget(100).build()))
                .capabilityInvoker(invocation -> {
                    invocations.incrementAndGet();
                    invocation.recordExecution(new com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityExecutionFacts(
                            0, 0, com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage.empty(), 0));
                    return RuntimeCapabilityResult.builder().content("检索到已发布资料").build();
                })
                        .taskStore(store).build())
                .build());

        assertInstanceOf(AgentRunResult.Completed.class, result);
        assertEquals("资料查询完成", result.content());
        assertEquals(2, store.tasks.size());
        assertEquals("查询已发布资料", store.tasks.getLast().getFirst().getSubject());
        assertEquals(Task.State.COMPLETED, store.tasks.getLast().getFirst().getState());
        assertEquals(1, invocations.get());
        ExecutionEvent delegation = sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.TOOL_STARTED)
                .filter(event -> event.getPayload() instanceof ExecutionEventPayload.Tool tool
                        && tool.isAgentDelegation())
                .findFirst()
                .orElseThrow();
        ExecutionEventPayload.Tool payload = (ExecutionEventPayload.Tool) delegation.getPayload();
        assertEquals(30L, payload.getDelegatedAgentId());
        assertEquals(store.invocations.getFirst().getStep().getId(), payload.getStepId());
        assertEquals(1, payload.getTaskRevision());
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
        AgentRuntime runtime = runtime(modelFactory, mock(TextTokenEstimator.class));
        RecordingStore store = new RecordingStore();
        RecordingSink sink = new RecordingSink();

        AgentRunResult result = runtime.run(AgentRunRequest.builder()
                .runId("run-clarification-1").userId("1").sessionId("run-clarification-1")
                .agentId(10L).agentVersionId(20L).role(AgentRole.SUPERVISOR)
                .modelCommand(command("按平台规则处理任务。", "帮我查询资料", config))
                .policy(ExecutionPolicy.builder().maxModelCalls(2).maxToolCalls(1)
                        .maxTotalTokens(200).maxFinalizationTokens(200).build())
                .eventSink(sink).initialSequenceNo(0).initialUsage(TokenUsage.empty())
                .supervisorConfig(SupervisorRuntimeConfig.builder()
                        .planPolicy(PlanPolicy.builder().maxSteps(3).maxReplans(0)
                                .maxProtocolRepairAttempts(0).build())
                        .maxDelegations(0).capabilities(List.of())
                .capabilityInvoker(invocation -> {
                    throw new AssertionError("澄清不应调用能力");
                })
                        .taskStore(store).build())
                .build());

        AgentRunResult.Clarification clarification = assertInstanceOf(AgentRunResult.Clarification.class, result);
        assertEquals("请补充需要查询的资料范围", clarification.clarification().getQuestion());
        assertEquals(List.of("产品规则", "订单记录"), clarification.clarification().options());
        assertTrue(sink.types().contains(ExecutionEventType.CLARIFICATION_REQUIRED));
        assertTrue(store.tasks.isEmpty());
        assertFalse(store.invocations.size() > 0);
    }

    @Test
    void shouldRejectSuccessfulTextWhenPlanStillHasUnfinishedTasks() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        StubChatModel model = StubChatModel.sequence(List.of(
                List.of(new ToolUseBlock("todo-1", "todo_write", Map.of("todos", List.of(
                        Map.of("content", "查询已发布资料", "status", "in_progress", "priority", "high"))),
                        "{\"todos\":[{\"content\":\"查询已发布资料\",\"status\":\"in_progress\",\"priority\":\"high\"}]}", null)),
                List.of(TextBlock.builder().text("抱歉，能力执行失败").build())));
        AgentScopeOpenAiChatModelFactory modelFactory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        AgentRuntime runtime = runtime(modelFactory, mock(TextTokenEstimator.class));

        RuntimeCapabilityException exception = assertThrows(RuntimeCapabilityException.class,
                () -> runtime.run(AgentRunRequest.builder()
                        .runId("run-incomplete-1").userId("1").sessionId("run-incomplete-1")
                        .agentId(10L).agentVersionId(20L).role(AgentRole.SUPERVISOR)
                        .modelCommand(command("按平台规则处理任务。", "查询资料", config))
                        .policy(ExecutionPolicy.builder().maxModelCalls(2).maxToolCalls(1)
                                .maxTotalTokens(200).maxFinalizationTokens(200).build())
                        .eventSink(new RecordingSink()).initialSequenceNo(0).initialUsage(TokenUsage.empty())
                        .supervisorConfig(SupervisorRuntimeConfig.builder()
                                .planPolicy(PlanPolicy.builder().maxSteps(3).maxReplans(0)
                                        .maxProtocolRepairAttempts(0).build())
                                .maxDelegations(1).capabilities(List.of(RuntimeCapability.builder()
                                .code("research").name("资料检索").type(RuntimeCapabilityType.AGENT).targetId(30L)
                                .risk(RuntimeCapabilityRisk.READ_ONLY).maxCalls(1).resultTokenBudget(100).build()))
                        .capabilityInvoker(invocation -> RuntimeCapabilityResult.builder().content("unused").build())
                                .taskStore(new RecordingStore()).build())
                        .build()));

        assertEquals("PLAN_INCOMPLETE", exception.getCode());
        assertFalse(exception.isRetryable());
    }

    @Test
    void shouldMergeDelegatedSequenceBeforePublishingChildFailure() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        StubChatModel model = StubChatModel.sequence(List.of(
                List.of(new ToolUseBlock("todo-1", "todo_write", Map.of("todos", List.of(
                        Map.of("content", "查询已发布资料", "status", "in_progress", "priority", "high"))),
                        "{\"todos\":[{\"content\":\"查询已发布资料\",\"status\":\"in_progress\",\"priority\":\"high\"}]}", null)),
                List.of(new ToolUseBlock("delegate-1", "agent_spawn",
                        Map.of("agent_id", "research", "task", "查询资料", "timeout_seconds", 0),
                        "{\"agent_id\":\"research\",\"task\":\"查询资料\",\"timeout_seconds\":0}", null)),
                List.of(new ToolUseBlock("wait-1", "wait_async_results",
                        Map.of("timeout_seconds", 1, "wait_all", true),
                        "{\"timeout_seconds\":1,\"wait_all\":true}", null)),
                List.of(TextBlock.builder().text("能力执行失败").build())));
        AgentScopeOpenAiChatModelFactory modelFactory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        RecordingSink sink = new RecordingSink();
        AgentRuntime runtime = runtime(modelFactory, mock(TextTokenEstimator.class));

        RuntimeCapabilityException exception = assertThrows(RuntimeCapabilityException.class,
                () -> runtime.run(AgentRunRequest.builder()
                        .runId("run-child-failure-1").userId("1").sessionId("run-child-failure-1")
                        .agentId(10L).agentVersionId(20L).role(AgentRole.SUPERVISOR)
                        .modelCommand(command("按平台规则处理任务。", "查询资料", config))
                        .policy(ExecutionPolicy.builder().maxModelCalls(4).maxToolCalls(2)
                                .maxTotalTokens(200).maxFinalizationTokens(200).build())
                        .eventSink(sink).initialSequenceNo(0).initialUsage(TokenUsage.empty())
                        .supervisorConfig(SupervisorRuntimeConfig.builder()
                                .planPolicy(PlanPolicy.builder().maxSteps(3).maxReplans(0)
                                        .maxProtocolRepairAttempts(0).build())
                                .maxDelegations(1).capabilities(List.of(RuntimeCapability.builder()
                                .code("research").name("资料检索").type(RuntimeCapabilityType.AGENT).targetId(30L)
                                .risk(RuntimeCapabilityRisk.READ_ONLY).maxCalls(1).resultTokenBudget(100).build()))
                        .capabilityInvoker(invocation -> {
                            invocation.recordExecution(
                                    new com.lingxi.workbench.agentruntime.plan.RuntimeCapabilityExecutionFacts(
                                            0, 0, TokenUsage.empty(), 50));
                            throw new RuntimeCapabilityException("CHILD_FAILED", "子能力执行失败", false, null);
                        })
                                .taskStore(new RecordingStore()).build())
                        .build()));

        assertEquals("CHILD_FAILED", exception.getCode());
        ExecutionEvent stepFailure = sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.STEP_FAILED)
                .findFirst()
                .orElseThrow();
        assertTrue(stepFailure.getSequenceNo() > 50);
        assertEquals(sink.events.size(), sink.events.stream().map(ExecutionEvent::getSequenceNo).distinct().count());
    }

    @Test
    void shouldStopSupervisorWhenDelegatedCapabilityRequiresHumanApproval() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        StubChatModel model = StubChatModel.sequence(List.of(
                List.of(new ToolUseBlock("todo-1", "todo_write", Map.of("todos", List.of(
                        Map.of("content", "读取个人信息", "status", "in_progress", "priority", "high"))),
                        "{\"todos\":[{\"content\":\"读取个人信息\",\"status\":\"in_progress\",\"priority\":\"high\"}]}", null)),
                List.of(new ToolUseBlock("delegate-1", "agent_spawn",
                        Map.of("agent_id", "profile", "task", "读取个人信息", "timeout_seconds", 0),
                        "{\"agent_id\":\"profile\",\"task\":\"读取个人信息\",\"timeout_seconds\":0}", null)),
                List.of(new ToolUseBlock("wait-1", "wait_async_results",
                        Map.of("timeout_seconds", 1, "wait_all", true),
                        "{\"timeout_seconds\":1,\"wait_all\":true}", null)),
                List.of(TextBlock.builder().text("不应继续生成").build())));
        AgentScopeOpenAiChatModelFactory modelFactory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        RecordingSink sink = new RecordingSink();
        AgentRuntime runtime = runtime(modelFactory, mock(TextTokenEstimator.class));
        RuntimeInterruption interruption = RuntimeInterruption.builder()
                .interactionId(91L).checkpointId(92L).fingerprint("approval-fingerprint")
                .title("读取个人信息").summary("需要用户确认读取个人信息").riskLevel("MEDIUM")
                .resourceScope("current-user").revision(1).build();

        AgentRunResult result = runtime.run(AgentRunRequest.builder()
                .runId("run-human-required-1").userId("1").sessionId("run-human-required-1")
                .agentId(10L).agentVersionId(20L).role(AgentRole.SUPERVISOR)
                .modelCommand(command("按平台规则处理任务。", "读取个人信息", config))
                .policy(ExecutionPolicy.builder().maxModelCalls(4).maxToolCalls(2)
                        .maxTotalTokens(200).maxFinalizationTokens(200).build())
                .eventSink(sink).initialSequenceNo(0).initialUsage(TokenUsage.empty())
                .supervisorConfig(SupervisorRuntimeConfig.builder()
                        .planPolicy(PlanPolicy.builder().maxSteps(3).maxReplans(0)
                                .maxProtocolRepairAttempts(0).build())
                        .maxDelegations(1).capabilities(List.of(RuntimeCapability.builder()
                        .code("profile").name("个人信息").type(RuntimeCapabilityType.AGENT).targetId(30L)
                        .risk(RuntimeCapabilityRisk.HIGH_RISK).maxCalls(1).resultTokenBudget(100).build()))
                .capabilityInvoker(invocation -> {
                    java.util.concurrent.locks.LockSupport.parkNanos(100_000_000L);
                    return RuntimeCapabilityResult.builder().interruption(interruption).build();
                })
                        .taskStore(new RecordingStore()).build())
                .build());

        AgentRunResult.Interrupted interrupted = assertInstanceOf(AgentRunResult.Interrupted.class, result);
        assertEquals(91L, interrupted.interruption().getInteractionId());
        assertTrue(sink.types().contains(ExecutionEventType.TOOL_FAILED));
        assertFalse(sink.types().contains(ExecutionEventType.RUN_FAILED));
        assertEquals(3, model.responseNo.get());
    }

    private static AgentRuntime runtime(AgentScopeOpenAiChatModelFactory modelFactory,
                                        TextTokenEstimator tokenCountEstimator) {
        return new AgentRuntime(mock(AgentScopeSpecialistStreamingGateway.class),
                new AgentScopeSupervisorGateway(modelFactory, tokenCountEstimator),
                new InMemoryAgentStateStore());
    }

    private static ChatStreamCommand command(String systemPrompt, String prompt,
                                             ChatModelRuntimeConfig modelConfig) {
        return ChatStreamCommand.builder().systemPrompt(systemPrompt).prompt(prompt)
                .history(List.of()).modelConfig(modelConfig).build();
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
        public long onEvent(ExecutionEvent event) {
            events.add(event);
            return event.getSequenceNo();
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
