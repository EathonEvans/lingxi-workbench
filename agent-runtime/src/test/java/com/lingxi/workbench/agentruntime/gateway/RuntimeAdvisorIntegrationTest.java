package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeContextAdvisor;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeModelAdvisor;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.model.SkillActivation;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeAdvisorIntegrationTest {

    @Test
    void shouldRunStreamingModelThroughChatClientAdvisors() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(
                org.springframework.ai.openai.OpenAiChatOptions.builder().model("test-model").build());
        Usage firstUsage = usage(6, 1, 7);
        Usage finalUsage = usage(6, 2, 8);
        ChatResponse firstResponse = response("答", firstUsage);
        ChatResponse finalResponse = response("案", finalUsage);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(firstResponse, finalResponse));
        OpenAiCompatibleChatModelFactory modelFactory = mock(OpenAiCompatibleChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(chatModel);
        RecordingSink sink = new RecordingSink();
        ExecutionState state = ExecutionState.builder()
                .runId("run-advisor")
                .agentId(10L)
                .agentVersionId(20L)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(1)
                        .maxToolCalls(0)
                        .maxTotalTokens(100)
                        .maxFinalizationTokens(100)
                        .build())
                .eventSink(sink)
                .build();
        SpringAiStreamingChatGateway gateway = new SpringAiStreamingChatGateway(
                modelFactory,
                ObservationRegistry.NOOP,
                mock(ToolCallingManager.class),
                new SpringAiChatClientFactory(ObservationRegistry.NOOP),
                new RuntimeContextAdvisor(),
                new RuntimeModelAdvisor(),
                new com.lingxi.workbench.agentruntime.hook.HumanApprovalHook(
                        new com.lingxi.workbench.agentruntime.checkpoint.SpringAiCheckpointCodec(),
                        new com.lingxi.workbench.agentruntime.checkpoint.ToolApprovalFingerprint()),
                new com.lingxi.workbench.agentruntime.checkpoint.SpringAiCheckpointCodec());

        List<ChatStreamChunk> chunks = gateway.stream(ChatStreamCommand.builder()
                        .prompt("问题")
                        .systemPrompt("系统")
                        .history(List.of())
                        .modelConfig(config)
                        .build(), state)
                .collectList()
                .block();

        assertEquals("答案", chunks.stream().map(ChatStreamChunk::content).reduce("", String::concat));
        assertEquals(8, state.tokenUsage().totalTokens());
        assertEquals(List.of(ExecutionEventType.MODEL_STARTED, ExecutionEventType.MODEL_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
        verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void shouldInjectSkillBeforePlatformToolPolicyOnEveryRuntimeModelCall() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder().build();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        Usage finalUsage = usage(6, 2, 8);
        ChatResponse finalResponse = response("完成", finalUsage);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(finalResponse));
        OpenAiCompatibleChatModelFactory modelFactory = mock(OpenAiCompatibleChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(chatModel);
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("search_docs")
                .description("检索文档")
                .inputSchema("{\"type\":\"object\"}")
                .build());
        AgentToolRuntimeConfig toolConfig = AgentToolRuntimeConfig.builder()
                .tools(List.of(tool(callback)))
                .executionContext(AgentToolExecutionContext.builder()
                        .executionId("run-skill")
                        .userId(1L)
                        .conversationId(2L)
                        .assistantMessageId(3L)
                        .build())
                .maxResultCharacters(1000)
                .build();
        RecordingSink sink = new RecordingSink();
        ExecutionState state = ExecutionState.builder()
                .runId("run-skill")
                .agentId(10L)
                .agentVersionId(20L)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(2)
                        .maxToolCalls(1)
                        .maxTotalTokens(100)
                        .maxFinalizationTokens(120)
                        .build())
                .eventSink(sink)
                .toolRuntimeConfig(toolConfig)
                .authorizedToolCodes(List.of("search_docs"))
                .build();
        state.activateSkill(skillActivation());
        SpringAiStreamingChatGateway gateway = new SpringAiStreamingChatGateway(
                modelFactory, ObservationRegistry.NOOP, mock(ToolCallingManager.class),
                new SpringAiChatClientFactory(ObservationRegistry.NOOP),
                new RuntimeContextAdvisor(), new RuntimeModelAdvisor(),
                new com.lingxi.workbench.agentruntime.hook.HumanApprovalHook(
                        new com.lingxi.workbench.agentruntime.checkpoint.SpringAiCheckpointCodec(),
                        new com.lingxi.workbench.agentruntime.checkpoint.ToolApprovalFingerprint()),
                new com.lingxi.workbench.agentruntime.checkpoint.SpringAiCheckpointCodec());

        gateway.stream(ChatStreamCommand.builder()
                        .prompt("问题")
                        .systemPrompt("Agent角色规则")
                        .history(List.of())
                        .modelConfig(config)
                        .toolRuntimeConfig(toolConfig)
                        .build(), state)
                .collectList().block();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        String systemPrompt = ((SystemMessage) promptCaptor.getValue().getInstructions().getFirst()).getText();
        assertTrue(systemPrompt.indexOf("先核对事实再回答") < systemPrompt.indexOf("## 平台工具调用规则"));
        assertTrue(systemPrompt.contains("必须调用该工具"));
        assertEquals(ExecutionEventType.SKILL_ACTIVATED, sink.events.getFirst().getType());
    }

    private static AgentTool tool(ToolCallback callback) {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .code("search_docs")
                .displayName("检索文档")
                .description("检索文档")
                .build();
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public ToolCallback callback() {
                return callback;
            }
        };
    }

    private static SkillActivation skillActivation() {
        SkillVersionSnapshot skill = SkillVersionSnapshot.builder()
                .schemaVersion(1)
                .code("fact_check")
                .name("事实核验")
                .instructions("先核对事实再回答")
                .activationMode(SkillActivationMode.ALWAYS_ON)
                .requestedToolCodes(List.of("search_docs"))
                .build();
        AgentSkillBindingSnapshot binding = AgentSkillBindingSnapshot.builder()
                .skillId(30L)
                .skillVersionId(31L)
                .versionNo(1)
                .checksum("checksum")
                .skill(skill)
                .effectiveToolCodes(List.of("search_docs"))
                .deniedToolCodes(List.of())
                .build();
        return new SkillActivation(binding, SkillActivationReason.ALWAYS_ON);
    }

    private static ChatResponse response(String content, Usage usage) {
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata);
    }

    private static Usage usage(int input, int output, int total) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(input);
        when(usage.getCompletionTokens()).thenReturn(output);
        when(usage.getTotalTokens()).thenReturn(total);
        return usage;
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
    }
}
