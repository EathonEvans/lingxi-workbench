package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.gateway.SpringAiChatClientFactory;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeContextAdvisor;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeModelAdvisor;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationMode;
import com.lingxi.workbench.infra.ai.skill.model.AgentSkillBindingSnapshot;
import com.lingxi.workbench.infra.ai.skill.model.SkillVersionSnapshot;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillRouterTest {

    @Test
    void shouldRouteFromMetadataOnlyAndConsumeUnifiedModelBudget() {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder()
                .structuredOutputSupported(false).build();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("router-model").build());
        ChatResponse response = response("{\"skillCodes\":[\"fact_check\"]}");
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        OpenAiCompatibleChatModelFactory modelFactory = mock(OpenAiCompatibleChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(chatModel);
        SkillRouter router = new SkillRouter(modelFactory,
                new SpringAiChatClientFactory(ObservationRegistry.NOOP),
                new RuntimeContextAdvisor(), new RuntimeModelAdvisor());
        RecordingSink sink = new RecordingSink();
        ExecutionState state = state(sink);
        AgentSkillBindingSnapshot candidate = binding();

        List<String> selected = router.route(command(config), state, List.of(candidate));

        assertEquals(List.of("fact_check"), selected);
        assertEquals(1, state.modelCalls());
        assertEquals(List.of(ExecutionEventType.MODEL_STARTED, ExecutionEventType.MODEL_COMPLETED),
                sink.events.stream().map(ExecutionEvent::getType).toList());
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String disclosed = prompt.getValue().getInstructions().stream().map(Message::getText)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(disclosed.contains("fact_check"));
        assertTrue(disclosed.contains("核验事实来源"));
        assertFalse(disclosed.contains("内部核验步骤正文"));
        assertFalse(disclosed.contains("平台工具调用规则"));
    }

    private static ChatStreamCommand command(ChatModelRuntimeConfig config) {
        return ChatStreamCommand.builder().prompt("核验这条消息").systemPrompt("不应进入路由")
                .history(List.of()).modelConfig(config).build();
    }

    private static ExecutionState state(RecordingSink sink) {
        return ExecutionState.builder().runId("skill-router-run").agentId(10L).agentVersionId(20L)
                .policy(ExecutionPolicy.builder().maxModelCalls(3).maxToolCalls(0)
                        .maxTotalTokens(100).maxFinalizationTokens(100)
                        .timeout(Duration.ofSeconds(3)).activeOutputGrace(Duration.ZERO).build())
                .eventSink(sink).authorizedToolCodes(List.of()).build();
    }

    private static AgentSkillBindingSnapshot binding() {
        return AgentSkillBindingSnapshot.builder().skillId(1L).skillVersionId(11L).versionNo(1)
                .checksum("checksum")
                .skill(SkillVersionSnapshot.builder().schemaVersion(1).code("fact_check").name("事实核验")
                        .description("核验事实来源").instructions("内部核验步骤正文")
                        .activationMode(SkillActivationMode.MODEL_ROUTED).requestedToolCodes(List.of()).build())
                .effectiveToolCodes(List.of()).deniedToolCodes(List.of()).build();
    }

    private static ChatResponse response(String content) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(5);
        when(usage.getCompletionTokens()).thenReturn(2);
        when(usage.getTotalTokens()).thenReturn(7);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata);
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
