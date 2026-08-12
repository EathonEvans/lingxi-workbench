package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.memory.config.MemoryChatModelResolver;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.workflow.model.ContextualizationPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryContextualizerTest {

    private final PromptRuntimeService promptRuntimeService = mock(PromptRuntimeService.class);
    private final MemoryChatModelResolver modelResolver = mock(MemoryChatModelResolver.class);
    private final ChatCompletionGateway completionGateway = mock(ChatCompletionGateway.class);
    private final QueryContextualizer contextualizer = new QueryContextualizer(
            promptRuntimeService, modelResolver, completionGateway, new PromptTemplateRenderer());

    @Test
    void shouldContextualizeWithPublishedPromptAndRecordUsage() {
        ContextualizationPolicy policy = policy(true);
        TokenUsage usage = TokenUsage.of(20, 5, 25);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new ChatCompletionResult<>("  退款订单 A-100  ", usage)));
        List<ChatMemoryMessageBO> history = List.of(
                new ChatMemoryMessageBO(1, MessageRole.USER, "帮我退款"),
                new ChatMemoryMessageBO(2, MessageRole.ASSISTANT, "请提供订单号"));

        QueryContextualizer.ContextualizationResult result = contextualizer.contextualize(
                policy, "就是刚才那个", new RuntimeVariables(), history);

        assertEquals("退款订单 A-100", result.query());
        assertEquals(usage, result.usage());
        ArgumentCaptor<ChatCompletionCommand> commandCaptor = ArgumentCaptor.forClass(ChatCompletionCommand.class);
        verify(completionGateway).complete(commandCaptor.capture());
        assertEquals(2, commandCaptor.getValue().history().size());
        assertEquals(256, commandCaptor.getValue().modelConfig().getMaxTokens());
    }

    @Test
    void shouldFallbackToOriginalAndKeepUsageWhenResultIsInvalid() {
        ContextualizationPolicy policy = policy(true);
        TokenUsage usage = TokenUsage.of(8, 2, 10);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new ChatCompletionResult<>("   ", usage)));

        QueryContextualizer.ContextualizationResult result = contextualizer.contextualize(
                policy, "原问题", new RuntimeVariables(), List.of());

        assertEquals("原问题", result.query());
        assertEquals(usage, result.usage());
    }

    @Test
    void shouldFailWhenPublishedPolicyDisallowsFallback() {
        ContextualizationPolicy policy = policy(false);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.error(new IllegalStateException("provider unavailable")));

        assertThrows(BusinessException.class, () -> contextualizer.contextualize(
                policy, "原问题", new RuntimeVariables(), List.of()));
    }

    @Test
    void shouldPropagateCancellationInterruptionInsteadOfFallingBack() {
        ContextualizationPolicy policy = policy(true);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.error(new InterruptedException("cancelled")));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> contextualizer.contextualize(
                policy, "原问题", new RuntimeVariables(), List.of()));

        assertTrue(hasInterruptedCause(exception));
    }

    private void prepareDependencies(ContextualizationPolicy policy) {
        when(promptRuntimeService.resolvePublished(policy.getPromptVersionId()))
                .thenReturn(new PromptRuntimeSnapshot(policy.getPromptVersionId(), "请结合历史将当前问题上下文化", List.of(), PromptRole.SYSTEM));
        when(modelResolver.resolve(policy.getChatModelInstanceId()))
                .thenReturn(ChatModelRuntimeConfig.builder().instanceId(policy.getChatModelInstanceId()).maxTokens(2048).build());
    }

    private static ContextualizationPolicy policy(boolean fallbackToOriginal) {
        return ContextualizationPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(4L)
                .promptVersionId(5L)
                .fallbackToOriginal(fallbackToOriginal)
                .maxOutputTokens(256)
                .maxOutputCharacters(1000)
                .timeoutSeconds(15)
                .build();
    }

    private static boolean hasInterruptedCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
