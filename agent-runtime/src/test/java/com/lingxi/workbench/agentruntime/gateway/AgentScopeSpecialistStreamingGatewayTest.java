package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeApprovedInvocation;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.context.AgentScopeConversationStateFactory;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeInterruptionRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.checkpoint.ToolApprovalFingerprint;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.ExecutionEvents;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.model.AgentRunRequest;
import com.lingxi.workbench.agentruntime.model.ExecutionBudget;
import com.lingxi.workbench.agentruntime.model.ExecutionInterruption;
import com.lingxi.workbench.agentruntime.model.ExecutionOutput;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionScope;
import com.lingxi.workbench.agentruntime.model.ExecutionTools;
import com.lingxi.workbench.agentruntime.model.SkillRuntimeCatalog;
import com.lingxi.workbench.agentruntime.skill.AgentScopePublishedSkillCatalog;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeChatModelResource;
import com.lingxi.workbench.infra.ai.chat.openai.AgentScopeOpenAiChatModelFactory;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import com.lingxi.workbench.infra.ai.tool.model.ToolSecurityPolicy;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.AgentState;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScopeSpecialistStreamingGatewayTest {

    @Test
    void shouldRunNoToolSpecialistWithMappedMessagesAndStableRuntimeEvents() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        StubChatModel model = new StubChatModel("AgentScope回答", ChatUsage.builder()
                .inputTokens(7).outputTokens(2).build());
        AgentScopeOpenAiChatModelFactory factory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(factory.create(config)).thenReturn(
                new AgentScopeChatModelResource(model, new OkHttpTransport()));
        RecordingSink sink = new RecordingSink();
        ChatStreamCommand command = command(config);
        Fixture fixture = fixture(state(sink, command), sink, null);
        fixture.events().start();
        AgentScopeSpecialistStreamingGateway gateway = new AgentScopeSpecialistStreamingGateway(factory,
                mock(ToolApprovalFingerprint.class));

        List<ChatStreamChunk> chunks = gateway.stream(fixture.request(), fixture.context(), skillCatalog())
                .collectList().block();

        assertEquals(List.of(new ChatStreamChunk("AgentScope回答")), chunks);
        assertEquals(9, fixture.budget().tokenUsage().totalTokens());
        assertEquals(List.of(ExecutionEventType.RUN_STARTED, ExecutionEventType.AGENT_STARTED,
                        ExecutionEventType.MODEL_STARTED, ExecutionEventType.MODEL_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
        assertEquals(List.of(MsgRole.USER, MsgRole.ASSISTANT, MsgRole.USER),
                model.messages.subList(model.messages.size() - 3, model.messages.size())
                        .stream().map(Msg::getRole).toList());
        assertEquals(List.of(), model.tools);
        assertTrue(model.messages.stream().anyMatch(message -> "系统规则".equals(message.getTextContent())));
    }

    @Test
    void shouldUseFinalizationBudgetForFinalOnlyRequest() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        StubChatModel model = new StubChatModel("收尾回答", ChatUsage.builder()
                .inputTokens(3).outputTokens(1).build());
        AgentScopeOpenAiChatModelFactory factory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(factory.create(config)).thenReturn(
                new AgentScopeChatModelResource(model, new OkHttpTransport()));
        RecordingSink sink = new RecordingSink();
        ChatStreamCommand command = command(config);
        Fixture fixture = fixture(state(sink, command), sink, null);
        fixture.events().start();
        fixture.budget().requestFinalization("测试收口");
        fixture.events().finalizing(fixture.budget().finalizationReason());

        List<ChatStreamChunk> chunks = new AgentScopeSpecialistStreamingGateway(factory,
                mock(ToolApprovalFingerprint.class))
                .stream(fixture.request(), fixture.context(), skillCatalog())
                .collectList().block();

        assertEquals(List.of(new ChatStreamChunk("收尾回答")), chunks);
        assertEquals(1, fixture.budget().modelCalls());
        assertEquals(List.of(ExecutionEventType.RUN_STARTED, ExecutionEventType.AGENT_STARTED,
                        ExecutionEventType.FINALIZING, ExecutionEventType.MODEL_STARTED,
                        ExecutionEventType.MODEL_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldLoadModelRoutedSkillThroughAgentScopeWithoutExposingItsInstructionsEarly() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        AgentSkillBindingSnapshot skill = skillBinding(1L, "fact_check", SkillActivationMode.MODEL_ROUTED);
        AgentScopePublishedSkillCatalog catalog = AgentScopePublishedSkillCatalog.from(
                new SkillRuntimeCatalog(List.of(skill), List.of()), true);
        String skillId = catalog.repository().getSkill("fact_check").getSkillId();
        StubChatModel model = StubChatModel.sequence(List.of(
                List.of(new ToolUseBlock("skill-load-1", "load_skill_through_path",
                        Map.of("skillId", skillId, "path", "SKILL.md"),
                        "{\"skillId\":\"" + skillId + "\",\"path\":\"SKILL.md\"}", null)),
                List.of(TextBlock.builder().text("核验完成").build())), ChatUsage.builder()
                .inputTokens(7).outputTokens(2).build());
        AgentScopeOpenAiChatModelFactory factory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(factory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        RecordingSink sink = new RecordingSink();
        ChatStreamCommand command = command(config);
        Fixture fixture = fixture(skillState(sink, command), sink, null);
        fixture.events().start();

        List<ChatStreamChunk> chunks = new AgentScopeSpecialistStreamingGateway(factory,
                mock(ToolApprovalFingerprint.class)).stream(fixture.request(), fixture.context(), catalog)
                .collectList().block();

        assertEquals(List.of(new ChatStreamChunk("核验完成")), chunks);
        assertEquals(2, fixture.budget().modelCalls());
        assertTrue(model.requests.getFirst().tools().stream()
                .anyMatch(schema -> "load_skill_through_path".equals(schema.getName())));
        String firstSystemPrompt = model.requests.getFirst().messages().stream()
                .filter(message -> message.getRole() == MsgRole.SYSTEM)
                .map(Msg::getTextContent)
                .reduce("", String::concat);
        assertTrue(firstSystemPrompt.contains("fact_check"));
        assertFalse(firstSystemPrompt.contains("fact_check-instructions"));
        assertEquals(List.of(SkillActivationReason.MODEL_ROUTED), sink.events.stream()
                .filter(event -> event.getType() == ExecutionEventType.SKILL_ACTIVATED)
                .map(ExecutionEvent::getPayload)
                .map(ExecutionEventPayload.Skill.class::cast)
                .map(ExecutionEventPayload.Skill::getReason)
                .toList());
    }

    @Test
    void shouldPersistAgentScopeStateAndInterruptBeforeApprovedToolExecution() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        AtomicInteger executions = new AtomicInteger();
        AgentTool approvalTool = tool(executions, true);
        StubChatModel model = new StubChatModel(List.of(new ToolUseBlock("call-1", "send_mail",
                Map.of("recipient", "user@example.com"))), ChatUsage.builder()
                .inputTokens(7).outputTokens(2).build());
        AgentScopeOpenAiChatModelFactory factory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(factory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        ToolApprovalFingerprint fingerprint = mock(ToolApprovalFingerprint.class);
        when(fingerprint.configHash(any())).thenReturn("mail-v1");
        when(fingerprint.approval(any(), any(), any(), any(), any(), any(), any())).thenReturn("approval-fp");
        AgentScopeCheckpointStore checkpointStore = mock(AgentScopeCheckpointStore.class);
        when(checkpointStore.interrupt(any(AgentScopeInterruptionRequest.class))).thenReturn(
                RuntimeInterruption.builder().interactionId(81L).checkpointId(82L)
                        .fingerprint("approval-fp").build());
        RecordingSink sink = new RecordingSink();
        ChatStreamCommand command = command(config);
        Fixture fixture = fixture(approvalState(sink, approvalTool, checkpointStore, command), sink, null);
        fixture.events().start();

        List<ChatStreamChunk> chunks = new AgentScopeSpecialistStreamingGateway(factory, fingerprint)
                .stream(fixture.request(), fixture.context(), skillCatalog())
                .collectList().block();

        assertTrue(chunks.isEmpty());
        assertEquals(0, executions.get());
        assertEquals(0, fixture.budget().toolCalls());
        assertEquals(ExecutionEventType.HUMAN_REQUIRED, sink.events.getLast().getType());
        org.mockito.ArgumentCaptor<AgentScopeInterruptionRequest> request =
                org.mockito.ArgumentCaptor.forClass(AgentScopeInterruptionRequest.class);
        org.mockito.Mockito.verify(checkpointStore).interrupt(request.capture());
        AgentScopeCheckpoint checkpoint = request.getValue().checkpoint();
        assertEquals("send_mail", checkpoint.getPendingToolCall().name());
        assertEquals("run-approval", checkpoint.getAgentSessionId());
        assertFalse(checkpoint.getAgentStateJson().isBlank());
    }

    @Test
    void shouldResumeExactApprovedToolFromPersistedAgentScopeState() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        AtomicInteger executions = new AtomicInteger();
        AgentTool approvalTool = tool(executions, true);
        AgentScopeOpenAiChatModelFactory factory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(factory.create(config)).thenReturn(
                new AgentScopeChatModelResource(new StubChatModel(List.of(new ToolUseBlock("call-1", "send_mail",
                        Map.of("recipient", "user@example.com"))), ChatUsage.builder()
                        .inputTokens(7).outputTokens(2).build()), new OkHttpTransport()),
                new AgentScopeChatModelResource(new StubChatModel("邮件发送完成", ChatUsage.builder()
                        .inputTokens(4).outputTokens(2).build()), new OkHttpTransport()));
        ToolApprovalFingerprint fingerprint = mock(ToolApprovalFingerprint.class);
        when(fingerprint.configHash(any())).thenReturn("mail-v1");
        when(fingerprint.approval(any(), any(), any(), any(), any(), any(), any())).thenReturn("approval-fp");
        AgentScopeCheckpointStore checkpointStore = mock(AgentScopeCheckpointStore.class);
        when(checkpointStore.interrupt(any(AgentScopeInterruptionRequest.class))).thenReturn(
                RuntimeInterruption.builder().interactionId(81L).checkpointId(82L)
                        .fingerprint("approval-fp").build());
        AgentScopeSpecialistStreamingGateway gateway = new AgentScopeSpecialistStreamingGateway(factory, fingerprint);
        ChatStreamCommand command = command(config);
        RecordingSink interruptedSink = new RecordingSink();
        Fixture interrupted = fixture(approvalState(interruptedSink, approvalTool, checkpointStore, command),
                interruptedSink, null);
        interrupted.events().start();

        gateway.stream(interrupted.request(), interrupted.context(), skillCatalog())
                .collectList().block();
        org.mockito.ArgumentCaptor<AgentScopeInterruptionRequest> request =
                org.mockito.ArgumentCaptor.forClass(AgentScopeInterruptionRequest.class);
        org.mockito.Mockito.verify(checkpointStore).interrupt(request.capture());
        AgentScopeCheckpoint checkpoint = request.getValue().checkpoint();
        when(checkpointStore.claimApproved(checkpoint, 81L, "approval-fp"))
                .thenReturn(new AgentScopeApprovedInvocation(91L, "approval-event", checkpoint.getPendingToolCall()));
        RecordingSink resumedSink = new RecordingSink();
        Fixture resumed = fixture(approvalState(resumedSink, approvalTool, checkpointStore, command),
                resumedSink, checkpoint);

        List<ChatStreamChunk> chunks = gateway.resume(resumed.request(), resumed.context(), skillCatalog(),
                checkpoint, 81L, "approval-fp")
                .collectList().block();

        assertEquals(List.of(new ChatStreamChunk("邮件发送完成")), chunks);
        assertEquals(1, executions.get());
        assertEquals(1, resumed.budget().toolCalls());
        org.mockito.Mockito.verify(checkpointStore).complete(91L, "邮件已发送");
    }

    @Test
    void shouldAllowPublishedNonApprovalToolThroughPermissionEngine() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        AtomicInteger executions = new AtomicInteger();
        AgentTool tool = tool(executions, false);
        StubChatModel model = StubChatModel.sequence(List.of(
                List.of(new ToolUseBlock("call-1", "send_mail", Map.of("recipient", "user@example.com"))),
                List.of(TextBlock.builder().text("邮件发送完成").build())), ChatUsage.builder()
                .inputTokens(7).outputTokens(2).build());
        AgentScopeOpenAiChatModelFactory factory = mock(AgentScopeOpenAiChatModelFactory.class);
        when(factory.create(config)).thenReturn(new AgentScopeChatModelResource(model, new OkHttpTransport()));
        ChatStreamCommand command = command(config);
        RecordingSink sink = new RecordingSink();
        Fixture fixture = fixture(approvalState(sink, tool, mock(AgentScopeCheckpointStore.class), command),
                sink, null);
        fixture.events().start();

        List<ChatStreamChunk> chunks = new AgentScopeSpecialistStreamingGateway(factory,
                mock(ToolApprovalFingerprint.class)).stream(fixture.request(), fixture.context(), skillCatalog())
                .collectList().block();

        assertEquals(List.of(new ChatStreamChunk("邮件发送完成")), chunks);
        assertEquals(1, executions.get());
        assertEquals(1, fixture.budget().toolCalls());
    }

    private static ChatStreamCommand command(ChatModelRuntimeConfig config) {
        return ChatStreamCommand.builder()
                .prompt("当前问题")
                .systemPrompt("系统规则")
                .history(List.of(
                        new ChatHistoryMessage(ChatHistoryMessage.Role.USER, "历史问题"),
                        new ChatHistoryMessage(ChatHistoryMessage.Role.ASSISTANT, "历史回答")))
                .modelConfig(config)
                .build();
    }

    private static Fixture fixture(AgentRunRequest request, RecordingSink sink, AgentScopeCheckpoint checkpoint) {
        RuntimeContext context = RuntimeContext.builder()
                .userId("1")
                .sessionId(request.getRunId())
                .agentState(AgentScopeConversationStateFactory.create(request.getModelCommand(), "1",
                        request.getRunId()))
                .put(AgentStateStore.class, new InMemoryAgentStateStore())
                .build();
        ExecutionBudget budget = new ExecutionBudget(request.getPolicy(),
                checkpoint == null ? 0 : checkpoint.getModelCalls(),
                checkpoint == null ? 0 : checkpoint.getToolCalls(),
                checkpoint == null ? TokenUsage.empty() : checkpoint.getTokenUsage());
        ExecutionTools tools = new ExecutionTools(request.getModelCommand().toolRuntimeConfig().orElse(null),
                request.getAuthorizedToolCodes(), request.getPolicy());
        ExecutionOutput output = new ExecutionOutput();
        if (checkpoint != null) {
            budget.restoreFinalization(checkpoint);
            tools.restore(checkpoint);
            output.restore(checkpoint.getOutput());
        }
        ExecutionEvents events = new ExecutionEvents(request.getRunId(), request.getAgentId(),
                request.getAgentVersionId(), ExecutionScope.RUN, sink,
                checkpoint == null ? 0L : checkpoint.getLastSequenceNo());
        context.put(ExecutionBudget.class, budget);
        context.put(ExecutionTools.class, tools);
        context.put(ExecutionEvents.class, events);
        context.put(ExecutionOutput.class, output);
        context.put(ExecutionInterruption.class, new ExecutionInterruption());
        return new Fixture(request, context, budget, events);
    }

    private static AgentScopePublishedSkillCatalog skillCatalog() {
        return AgentScopePublishedSkillCatalog.from(SkillRuntimeCatalog.empty(), true);
    }

    private static AgentRunRequest skillState(RecordingSink sink, ChatStreamCommand command) {
        return AgentRunRequest.builder()
                .runId("run-agentscope-skill")
                .agentId(10L)
                .agentVersionId(20L)
                .role(AgentRole.SPECIALIST)
                .modelCommand(command)
                .userId("1")
                .sessionId("run-agentscope-skill")
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(0)
                        .maxTotalTokens(100)
                        .maxFinalizationTokens(100)
                        .build())
                .eventSink(sink)
                .build();
    }

    private static AgentSkillBindingSnapshot skillBinding(Long skillId, String code, SkillActivationMode mode) {
        return AgentSkillBindingSnapshot.builder()
                .skillId(skillId)
                .skillVersionId(skillId + 10)
                .versionNo(1)
                .checksum("checksum-" + code)
                .skill(SkillVersionSnapshot.builder()
                        .schemaVersion(1)
                        .code(code)
                        .name(code + "名称")
                        .description(code + "描述")
                        .instructions(code + "-instructions")
                        .activationMode(mode)
                        .requestedToolCodes(List.of())
                        .build())
                .effectiveToolCodes(List.of())
                .deniedToolCodes(List.of())
                .build();
    }

    private static AgentRunRequest state(RecordingSink sink, ChatStreamCommand command) {
        return AgentRunRequest.builder()
                .runId("run-agentscope")
                .agentId(10L)
                .agentVersionId(20L)
                .role(AgentRole.SPECIALIST)
                .modelCommand(command)
                .userId("1")
                .sessionId("run-agentscope")
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(1)
                        .maxToolCalls(0)
                        .maxTotalTokens(100)
                        .maxFinalizationTokens(100)
                        .build())
                .eventSink(sink)
                .build();
    }

    private static AgentRunRequest approvalState(RecordingSink sink, AgentTool tool,
                                                 AgentScopeCheckpointStore checkpointStore,
                                                 ChatStreamCommand command) {
        AgentToolRuntimeConfig toolConfig = AgentToolRuntimeConfig.builder()
                .tools(List.of(tool))
                .executionContext(AgentToolExecutionContext.builder()
                        .executionId("run-approval")
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .maxResultCharacters(1000)
                .build();
        ChatStreamCommand toolCommand = ChatStreamCommand.builder()
                .prompt(command.prompt())
                .systemPrompt(command.systemPrompt())
                .history(command.history())
                .modelConfig(command.modelConfig())
                .toolRuntimeConfig(toolConfig)
                .build();
        return AgentRunRequest.builder()
                .runId("run-approval")
                .agentId(10L)
                .agentVersionId(20L)
                .role(AgentRole.SPECIALIST)
                .modelCommand(toolCommand)
                .userId("1")
                .sessionId("run-approval")
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(1)
                        .maxTotalTokens(100)
                        .maxFinalizationTokens(100)
                        .build())
                .eventSink(sink)
                .authorizedToolCodes(List.of("send_mail"))
                .checkpointSeed(AgentScopeCheckpoint.builder()
                        .query("请发送邮件")
                        .capabilityCode("mail_agent")
                        .applicationVersionId(30L)
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .checkpointStore(checkpointStore)
                .build();
    }

    private record Fixture(AgentRunRequest request, RuntimeContext context, ExecutionBudget budget,
                           ExecutionEvents events) {
    }

    private static AgentTool tool(AtomicInteger executions, boolean approvalRequired) {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("send_mail")
                .displayName("发送邮件")
                .description("向指定收件人发送邮件")
                .configurationHash("mail-v1")
                .securityPolicy(ToolSecurityPolicy.builder().approvalRequired(approvalRequired).build())
                .build();
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties",
                        Map.of("recipient", Map.of("type", "string")));
            }

            @Override
            public String execute(AgentToolRequest request) {
                executions.incrementAndGet();
                return "邮件已发送";
            }
        };
    }

    private static final class StubChatModel extends ChatModelBase {

        private final List<List<ContentBlock>> responses;
        private final ChatUsage usage;
        private final AtomicInteger responseNo = new AtomicInteger();
        private final List<Request> requests = new ArrayList<>();
        private List<Msg> messages = List.of();
        private List<ToolSchema> tools = List.of();

        private StubChatModel(String answer, ChatUsage usage) {
            this(List.of(TextBlock.builder().text(answer).build()), usage);
        }

        private StubChatModel(List<ContentBlock> content, ChatUsage usage) {
            this(List.of(content), usage, true);
        }

        private StubChatModel(List<List<ContentBlock>> responses, ChatUsage usage, boolean sequenced) {
            this.responses = responses;
            this.usage = usage;
        }

        private static StubChatModel sequence(List<List<ContentBlock>> responses, ChatUsage usage) {
            return new StubChatModel(responses, usage, true);
        }

        @Override
        public String getModelName() {
            return "stub-model";
        }

        @Override
        protected Flux<ChatResponse> doStream(List<Msg> messages, List<ToolSchema> tools,
                                              GenerateOptions options) {
            this.messages = List.copyOf(messages);
            this.tools = List.copyOf(tools);
            this.requests.add(new Request(this.messages, this.tools));
            List<ContentBlock> content = responses.get(Math.min(responseNo.getAndIncrement(), responses.size() - 1));
            return Flux.just(ChatResponse.builder()
                    .content(content)
                    .usage(usage)
                    .finishReason(content.stream().anyMatch(ToolUseBlock.class::isInstance) ? "tool_calls" : "stop")
                    .build());
        }

        private record Request(List<Msg> messages, List<ToolSchema> tools) {
        }
    }

    /** 单元测试模拟跨实例 StateStore 协议，不回退生产运行时的 Redis 实现。 */
    private static final class InMemoryAgentStateStore implements AgentStateStore {

        private final Map<String, String> states = new ConcurrentHashMap<>();

        @Override
        public void save(String userId, String sessionId, String key, State value) {
            if (!(value instanceof AgentState agentState)) {
                throw new IllegalArgumentException("测试StateStore只接受AgentState");
            }
            states.put(storageKey(userId, sessionId, key), agentState.toJson());
        }

        @Override
        public void save(String userId, String sessionId, String key, List<? extends State> values) {
            // Specialist 仅使用 AgentState 单值；列表状态不是本运行时契约。
        }

        @Override
        public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
            String stateJson = states.get(storageKey(userId, sessionId, key));
            if (stateJson == null || type != AgentState.class) {
                return Optional.empty();
            }
            return Optional.of(type.cast(AgentState.fromJsonString(stateJson)));
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
    }
}
