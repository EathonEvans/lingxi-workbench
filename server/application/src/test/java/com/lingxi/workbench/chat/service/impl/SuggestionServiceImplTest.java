package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.config.SuggestionProperties;
import com.lingxi.workbench.chat.model.bo.SuggestionItemBO;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SuggestionServiceImplTest {

    private final PromptRuntimeService promptRuntimeService = mock(PromptRuntimeService.class);
    private final ModelRuntimeConfigService modelRuntimeConfigService = mock(ModelRuntimeConfigService.class);
    private final ChatCompletionGateway completionGateway = mock(ChatCompletionGateway.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SuggestionProperties properties = new SuggestionProperties();
    private final SuggestionServiceImpl suggestionService = new SuggestionServiceImpl(
            promptRuntimeService, modelRuntimeConfigService, completionGateway, properties, meterRegistry);

    @Test
    void shouldSkipEvaluationWhenDisabled() {
        properties.setEnabled(false);

        Optional<List<SuggestionItemBO>> result = suggestionService.evaluate("一段回答");

        assertTrue(result.isEmpty());
        verifyNoInteractions(promptRuntimeService, modelRuntimeConfigService, completionGateway);
    }

    @Test
    void shouldSkipEvaluationWhenModelInstanceMissing() {
        properties.setEnabled(true);
        properties.setModelInstanceId(null);

        Optional<List<SuggestionItemBO>> result = suggestionService.evaluate("一段回答");

        assertTrue(result.isEmpty());
        verifyNoInteractions(promptRuntimeService, modelRuntimeConfigService, completionGateway);
    }

    @Test
    void shouldSkipEvaluationWhenAnswerIsBlank() {
        properties.setEnabled(true);
        properties.setModelInstanceId(9L);

        Optional<List<SuggestionItemBO>> result = suggestionService.evaluate("   ");

        assertTrue(result.isEmpty());
        verifyNoInteractions(promptRuntimeService, modelRuntimeConfigService, completionGateway);
    }

    @Test
    void shouldEvaluateWithPublishedPromptAndParseSuggestions() {
        properties.setEnabled(true);
        properties.setModelInstanceId(9L);
        prepareDependencies();
        when(completionGateway.complete(any())).thenReturn(Mono.just(new ChatCompletionResult<>(
                "```json\n[{\"title\":\"整理文档\",\"prompt\":\"把上面的结果整理成文档\"},"
                        + "{\"title\":\"延伸分析\",\"prompt\":\"再深入分析一下成本构成\"}]\n```",
                TokenUsage.of(100, 30, 130), "STOP", 0)));

        Optional<List<SuggestionItemBO>> result = suggestionService.evaluate("回答内容");

        List<SuggestionItemBO> suggestions = result.orElseThrow();
        assertEquals(2, suggestions.size());
        assertEquals("suggestion-1", suggestions.get(0).id());
        assertEquals("整理文档", suggestions.get(0).title());
        assertEquals("把上面的结果整理成文档", suggestions.get(0).prompt());

        ArgumentCaptor<ChatCompletionCommand> commandCaptor = ArgumentCaptor.forClass(ChatCompletionCommand.class);
        verify(completionGateway).complete(commandCaptor.capture());
        assertEquals("回答内容", commandCaptor.getValue().prompt());
        assertTrue(commandCaptor.getValue().systemPrompt().contains("后续行动建议"));
        assertEquals(512, commandCaptor.getValue().modelConfig().getMaxTokens());
        assertEquals(1.0, successCount());
        assertEquals(0.0, failureCount());
    }

    @Test
    void shouldCapSuggestionsAndDropInvalidEntries() {
        properties.setEnabled(true);
        properties.setModelInstanceId(9L);
        properties.setMaxItems(2);
        prepareDependencies();
        String oversizedPrompt = "超".repeat(300);
        when(completionGateway.complete(any())).thenReturn(Mono.just(new ChatCompletionResult<>(
                "[{\"title\":\"有效建议\",\"prompt\":\"发送这个问题\"},"
                        + "{\"title\":\"\",\"prompt\":\"空标题应丢弃\"},"
                        + "{\"title\":\"超长建议\",\"prompt\":\"" + oversizedPrompt + "\"},"
                        + "{\"title\":\"建议三\",\"prompt\":\"发送第三个问题\"}]",
                TokenUsage.of(100, 30, 130), "STOP", 0)));

        Optional<List<SuggestionItemBO>> result = suggestionService.evaluate("回答内容");

        List<SuggestionItemBO> suggestions = result.orElseThrow();
        assertEquals(2, suggestions.size());
        assertEquals("有效建议", suggestions.get(0).title());
        assertEquals("建议三", suggestions.get(1).title());
    }

    @Test
    void shouldReturnEmptyWhenModelOutputIsNotJsonArray() {
        properties.setEnabled(true);
        properties.setModelInstanceId(9L);
        prepareDependencies();
        when(completionGateway.complete(any())).thenReturn(Mono.just(new ChatCompletionResult<>(
                "我觉得没有需要建议的内容", TokenUsage.of(100, 30, 130), "STOP", 0)));

        Optional<List<SuggestionItemBO>> result = suggestionService.evaluate("回答内容");

        assertTrue(result.isEmpty());
        assertEquals(0.0, successCount());
        assertEquals(1.0, failureCount());
    }

    @Test
    void shouldDegradeToEmptyWhenModelCallFails() {
        properties.setEnabled(true);
        properties.setModelInstanceId(9L);
        prepareDependencies();
        when(completionGateway.complete(any())).thenReturn(Mono.error(new IllegalStateException("模型超时")));

        Optional<List<SuggestionItemBO>> result = suggestionService.evaluate("回答内容");

        assertTrue(result.isEmpty());
        assertEquals(0.0, successCount());
        assertEquals(1.0, failureCount());
    }

    @Test
    void shouldDegradeToEmptyWhenPromptIsMissing() {
        properties.setEnabled(true);
        properties.setModelInstanceId(9L);
        when(promptRuntimeService.resolveLatestPublishedByCode("chat_answer_suggestion")).thenReturn(Optional.empty());

        Optional<List<SuggestionItemBO>> result = suggestionService.evaluate("回答内容");

        assertTrue(result.isEmpty());
        verifyNoInteractions(completionGateway);
        assertEquals(1.0, failureCount());
    }

    @Test
    void shouldTruncateOverlongAnswerBeforeEvaluation() {
        properties.setEnabled(true);
        properties.setModelInstanceId(9L);
        properties.setMaxAnswerCharacters(100);
        prepareDependencies();
        when(completionGateway.complete(any())).thenReturn(Mono.just(new ChatCompletionResult<>(
                "[]", TokenUsage.of(10, 1, 11), "STOP", 0)));
        String overlongAnswer = "长".repeat(500);

        suggestionService.evaluate(overlongAnswer);

        ArgumentCaptor<ChatCompletionCommand> commandCaptor = ArgumentCaptor.forClass(ChatCompletionCommand.class);
        verify(completionGateway).complete(commandCaptor.capture());
        assertEquals(100, commandCaptor.getValue().prompt().length());
    }

    private void prepareDependencies() {
        when(promptRuntimeService.resolveLatestPublishedByCode("chat_answer_suggestion"))
                .thenReturn(Optional.of(new PromptRuntimeSnapshot(
                        1L, "你是工作台助手中的后续行动建议组件。基于助手刚给出的回答提出用户可能的后续行动建议。",
                        List.of(), PromptRole.SYSTEM)));
        when(modelRuntimeConfigService.getChatModelConfig(9L))
                .thenReturn(ChatModelRuntimeConfig.builder().instanceId(9L).build());
    }

    private double successCount() {
        return meterRegistry.get("lingxi.chat.suggestion.eval.count").tag("outcome", "success").counter().count();
    }

    private double failureCount() {
        return meterRegistry.get("lingxi.chat.suggestion.eval.count").tag("outcome", "failure").counter().count();
    }
}
