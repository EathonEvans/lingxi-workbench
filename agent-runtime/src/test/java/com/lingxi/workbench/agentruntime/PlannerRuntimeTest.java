package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventType;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.agentruntime.gateway.SpringAiChatClientFactory;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeContextAdvisor;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeModelAdvisor;
import com.lingxi.workbench.agentruntime.model.ExecutionPolicy;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionAction;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionValidationContext;
import com.lingxi.workbench.agentruntime.planner.PlannerRunRequest;
import com.lingxi.workbench.agentruntime.planner.PlannerRunResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
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
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlannerRuntimeTest {

    @Test
    void shouldCreateTypedDecisionAndPublishUnifiedLifecycle() {
        Fixture fixture = fixture(false, response(validDirectJson(), 8, 3, 11));

        PlannerRunResult result = fixture.runtime.run(request(fixture.config, fixture.sink, 1, 1));

        assertEquals(PlannerDecisionAction.EXECUTE_CAPABILITY, result.decision().getAction());
        assertEquals("research", result.decision().getCapabilityCode());
        assertEquals(11, result.tokenUsage().totalTokens());
        assertEquals(1, result.modelCalls());
        assertEquals(List.of(ExecutionEventType.PLANNING, ExecutionEventType.MODEL_STARTED,
                        ExecutionEventType.MODEL_COMPLETED, ExecutionEventType.PLAN_CREATED),
                fixture.sink.events.stream().map(ExecutionEvent::getType).toList());
    }

    @Test
    void shouldRepairStrictProtocolAndBusinessValidationWithinBudget() {
        Fixture fixture = fixture(false,
                response("{\"action\":\"EXECUTE_CAPABILITY\",\"capabilityCode\":\"research\",\"unknown\":true}",
                        5, 2, 7),
                response("{\"action\":\"EXECUTE_CAPABILITY\",\"capabilityCode\":\"unauthorized\"}", 5, 2, 7),
                response(validDirectJson(), 5, 2, 7));

        PlannerRunResult result = fixture.runtime.run(request(fixture.config, fixture.sink, 1, 2));

        assertEquals("research", result.decision().getCapabilityCode());
        assertEquals(3, result.modelCalls());
        assertEquals(21, result.tokenUsage().totalTokens());
        verify(fixture.chatModel, times(3)).call(any(Prompt.class));
    }

    @Test
    void shouldReturnSafeClarificationAfterBoundedRepairs() {
        Fixture fixture = fixture(false,
                response("{\"action\":\"EXECUTE_CAPABILITY\",\"capabilityCode\":\"unauthorized\"}", 4, 1, 5),
                response("{\"action\":\"EXECUTE_PLAN\",\"plan\":null}", 4, 1, 5));

        PlannerRunResult result = fixture.runtime.run(request(fixture.config, fixture.sink, 2, 8));

        assertEquals(PlannerDecisionAction.CLARIFY, result.decision().getAction());
        assertEquals(3, result.modelCalls());
        assertEquals(15, result.tokenUsage().totalTokens());
        assertEquals(ExecutionEventType.REPLANNING, fixture.sink.events.getFirst().getType());
    }

    @Test
    void shouldUseNativeJsonSchemaOnlyWhenModelDeclaresCapability() {
        Fixture nativeFixture = fixture(true, response(validDirectJson(), 5, 2, 7));
        nativeFixture.runtime.run(request(nativeFixture.config, nativeFixture.sink, 1, 0));
        Fixture portableFixture = fixture(false, response(validDirectJson(), 5, 2, 7));
        portableFixture.runtime.run(request(portableFixture.config, portableFixture.sink, 1, 0));

        ArgumentCaptor<Prompt> nativePrompt = ArgumentCaptor.forClass(Prompt.class);
        verify(nativeFixture.chatModel).call(nativePrompt.capture());
        OpenAiChatOptions nativeOptions = (OpenAiChatOptions) nativePrompt.getValue().getOptions();
        assertNotNull(nativeOptions.getResponseFormat());
        assertEquals(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA,
                nativeOptions.getResponseFormat().getType());
        assertTrue(nativeOptions.getResponseFormat().getJsonSchema().contains("capabilityCode"));
        ArgumentCaptor<Prompt> portablePrompt = ArgumentCaptor.forClass(Prompt.class);
        verify(portableFixture.chatModel).call(portablePrompt.capture());
        assertNull(((OpenAiChatOptions) portablePrompt.getValue().getOptions()).getResponseFormat());
        String systemPrompt = ((SystemMessage) portablePrompt.getValue().getInstructions().getFirst()).getText();
        assertTrue(systemPrompt.contains("JSON Schema"));
    }

    private static PlannerRunRequest request(ChatModelRuntimeConfig config, RuntimeEventSink sink,
                                             int planNo, int repairAttempts) {
        return PlannerRunRequest.builder()
                .runId("run-planner")
                .agentId(10L)
                .agentVersionId(20L)
                .planNo(planNo)
                .systemPrompt("你是规划器")
                .userPrompt("完成研究任务")
                .history(List.of())
                .modelConfig(config)
                .validationContext(PlannerDecisionValidationContext.builder()
                        .authorizedCapabilityCodes(Set.of("research"))
                        .maxSteps(4)
                        .build())
                .maxProtocolRepairAttempts(repairAttempts)
                .policy(ExecutionPolicy.builder()
                        .maxModelCalls(3)
                        .maxToolCalls(0)
                        .maxTotalTokens(100)
                        .maxFinalizationTokens(100)
                        .timeout(Duration.ofSeconds(3))
                        .activeOutputGrace(Duration.ZERO)
                        .build())
                .eventSink(sink)
                .initialSequenceNo(0)
                .build();
    }

    private static Fixture fixture(boolean structuredOutputSupported, ChatResponse... responses) {
        ChatModelRuntimeConfig config = ChatModelRuntimeConfig.builder()
                .structuredOutputSupported(structuredOutputSupported)
                .build();
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().model("planner-model").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(responses[0], remaining(responses));
        OpenAiCompatibleChatModelFactory modelFactory = mock(OpenAiCompatibleChatModelFactory.class);
        when(modelFactory.create(config)).thenReturn(chatModel);
        RecordingSink sink = new RecordingSink();
        PlannerRuntime runtime = new PlannerRuntime(modelFactory,
                new SpringAiChatClientFactory(ObservationRegistry.NOOP),
                new RuntimeContextAdvisor(), new RuntimeModelAdvisor());
        return new Fixture(runtime, config, chatModel, sink);
    }

    private static ChatResponse[] remaining(ChatResponse[] responses) {
        if (responses.length < 2) {
            return new ChatResponse[0];
        }
        return java.util.Arrays.copyOfRange(responses, 1, responses.length);
    }

    private static ChatResponse response(String content, int input, int output, int total) {
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(input);
        when(usage.getCompletionTokens()).thenReturn(output);
        when(usage.getTotalTokens()).thenReturn(total);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata);
    }

    private static String validDirectJson() {
        return "{\"action\":\"EXECUTE_CAPABILITY\",\"capabilityCode\":\"research\"}";
    }

    private record Fixture(PlannerRuntime runtime, ChatModelRuntimeConfig config,
                           OpenAiChatModel chatModel, RecordingSink sink) {
    }

    private static final class RecordingSink implements RuntimeEventSink {
        private final List<ExecutionEvent> events = new ArrayList<>();

        @Override
        public void onEvent(ExecutionEvent event) {
            events.add(event);
        }

        @Override
        public void onText(ChatStreamChunk chunk) {
            // Planner 不产生文本流。
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
