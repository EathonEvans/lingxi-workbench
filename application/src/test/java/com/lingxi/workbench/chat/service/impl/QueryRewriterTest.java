package com.lingxi.workbench.chat.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueryRewriterTest {

    private final PromptRuntimeService promptRuntimeService = mock(PromptRuntimeService.class);
    private final ModelRuntimeConfigService modelRuntimeConfigService = mock(ModelRuntimeConfigService.class);
    private final ChatCompletionGateway completionGateway = mock(ChatCompletionGateway.class);
    private final QueryRewriter queryRewriter = new QueryRewriter(
            promptRuntimeService, modelRuntimeConfigService, completionGateway, new PromptTemplateRenderer());

    @Test
    void shouldRewriteWithPublishedPromptAndPreserveModelExtensionParameters() {
        ContextualizationPolicy policy = policy(true);
        TokenUsage usage = TokenUsage.of(20, 5, 25);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new ChatCompletionResult<>("  退款订单 A-100  ", usage, "STOP", 0)));
        List<ChatMemoryMessageBO> history = List.of(
                new ChatMemoryMessageBO(1, MessageRole.USER, "帮我退款"),
                new ChatMemoryMessageBO(2, MessageRole.ASSISTANT, "请提供订单号"));

        QueryRewriter.RewriteResult result = queryRewriter.rewrite(
                policy, "就是刚才那个", runtimeVariables("就是刚才那个"), history);

        assertEquals("退款订单 A-100", result.query());
        assertEquals(usage, result.usage());
        ArgumentCaptor<ChatCompletionCommand> commandCaptor = ArgumentCaptor.forClass(ChatCompletionCommand.class);
        verify(completionGateway).complete(commandCaptor.capture());
        assertEquals(0, commandCaptor.getValue().history().size());
        assertEquals("请严格执行系统中的问题重写任务。", commandCaptor.getValue().prompt());
        assertTrue(commandCaptor.getValue().systemPrompt().contains("帮我退款"));
        assertEquals(256, commandCaptor.getValue().modelConfig().getMaxTokens());
        assertEquals("disabled", commandCaptor.getValue().modelConfig().getExtensionParameters()
                .getJSONObject("thinking").getString("type"));
    }

    @Test
    void shouldKeepOriginalWithoutCallingModelWhenHistoryIsEmpty() {
        QueryRewriter.RewriteResult result = queryRewriter.rewrite(
                policy(true), "原问题", new RuntimeVariables(), List.of());

        assertEquals("原问题", result.query());
        assertEquals(TokenUsage.empty(), result.usage());
        assertEquals(0, result.modelCalls());
        verifyNoInteractions(promptRuntimeService, modelRuntimeConfigService, completionGateway);
    }

    @Test
    void shouldKeepStandaloneSocialTurnWithoutCallingModel() {
        QueryRewriter.RewriteResult result = queryRewriter.rewrite(
                policy(true), "你好！", new RuntimeVariables(), history());

        assertEquals("你好！", result.query());
        assertEquals(0, result.modelCalls());
        verifyNoInteractions(promptRuntimeService, modelRuntimeConfigService, completionGateway);
    }

    @Test
    void shouldFallbackToOriginalAndKeepUsageWhenResultIsInvalid() {
        ContextualizationPolicy policy = policy(true);
        TokenUsage usage = TokenUsage.of(8, 256, 264);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new ChatCompletionResult<>("   ", usage, "LENGTH", 820)));

        QueryRewriter.RewriteResult result = queryRewriter.rewrite(
                policy, "原问题", runtimeVariables("原问题"), history());

        assertEquals("原问题", result.query());
        assertEquals(usage, result.usage());
        assertEquals(1, result.modelCalls());
    }

    @Test
    void shouldFallbackWhenModelAnswersInsteadOfRewriting() {
        ContextualizationPolicy policy = policy(true);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new ChatCompletionResult<>(
                        "您好！我是灵犀求职教练。\n1. JD匹配度分析\n2. 简历优化", TokenUsage.of(80, 30, 110), "STOP", 0)));

        QueryRewriter.RewriteResult result = queryRewriter.rewrite(
                policy, "继续", runtimeVariables("继续"), history());

        assertEquals("继续", result.query());
        assertEquals(1, result.modelCalls());
    }

    @Test
    void shouldFallbackWhenSingleLineOutputExpandsLikeAnAnswer() {
        ContextualizationPolicy policy = policy(true);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(new ChatCompletionResult<>(
                        "这是回答。".repeat(30), TokenUsage.of(80, 90, 170), "STOP", 0)));

        QueryRewriter.RewriteResult result = queryRewriter.rewrite(
                policy, "继续分析", runtimeVariables("继续分析"), history());

        assertEquals("继续分析", result.query());
        assertEquals(1, result.modelCalls());
    }

    @Test
    void shouldFailWhenPublishedPolicyDisallowsFallback() {
        ContextualizationPolicy policy = policy(false);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.error(new IllegalStateException("provider unavailable")));

        assertThrows(BusinessException.class, () -> queryRewriter.rewrite(
                policy, "原问题", runtimeVariables("原问题"), history()));
    }

    @Test
    void shouldPropagateCancellationInterruptionInsteadOfFallingBack() {
        ContextualizationPolicy policy = policy(true);
        prepareDependencies(policy);
        when(completionGateway.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.error(new InterruptedException("cancelled")));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> queryRewriter.rewrite(
                policy, "原问题", runtimeVariables("原问题"), history()));

        assertTrue(hasInterruptedCause(exception));
    }

    private void prepareDependencies(ContextualizationPolicy policy) {
        when(promptRuntimeService.resolvePublished(policy.getPromptVersionId()))
                .thenReturn(new PromptRuntimeSnapshot(
                        policy.getPromptVersionId(),
                        "历史：{{query.contextualization_history}}\n当前问题：{{user.query}}",
                        List.of(
                                new PromptRuntimeVariableDefinition(
                                        "query.contextualization_history", "MESSAGE_LIST", true),
                                new PromptRuntimeVariableDefinition("user.query", "STRING", true)),
                        PromptRole.SYSTEM));
        when(modelRuntimeConfigService.getChatModelConfig(policy.getChatModelInstanceId()))
                .thenReturn(ChatModelRuntimeConfig.builder()
                        .instanceId(policy.getChatModelInstanceId())
                        .maxTokens(2048)
                        .extensionParameters(JSONObject.of("thinking", JSONObject.of("type", "disabled")))
                        .build());
    }

    private static List<ChatMemoryMessageBO> history() {
        return List.of(new ChatMemoryMessageBO(1, MessageRole.USER, "历史问题"));
    }

    private static RuntimeVariables runtimeVariables(String query) {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_QUERY, query);
        return variables;
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
