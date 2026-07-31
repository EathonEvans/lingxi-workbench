package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class QueryRewriteNodeTest {

    @Test
    void shouldRewriteFollowUpAndRecordUsage() {
        AtomicInteger totalTokens = new AtomicInteger();
        ChatCompletionGateway gateway = command -> {
            assertEquals("它的续航怎么样", command.prompt());
            assertEquals(2, command.history().size());
            assertTrue(command.systemPrompt().contains(
                    "当前问题：它的续航怎么样"));
            assertTrue(command.systemPrompt().contains(
                    "介绍一下 iPhone 16 Pro"));
            return Mono.just(new ChatCompletionResult("  iPhone 16 Pro 的续航怎么样？\n", TokenUsage.of(30, 10, 40)));
        };
        ModelChatContext context = enabledContext(
                true,
                totalTokens);

        new QueryRewriteNode(gateway, new PromptTemplateRenderer())
                .process(context);

        assertEquals(
                "iPhone 16 Pro 的续航怎么样？",
                context.retrievalQuery());
        assertEquals(40, totalTokens.get());
    }

    @Test
    void shouldUseOriginalQueryWithoutCallingModelWhenDisabled() {
        ChatCompletionGateway gateway = command -> {
            fail("关闭问题改写时不应调用模型");
            return Mono.empty();
        };
        RuntimeVariables variables = variables();
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(variables)
                .queryRewriteConfig(QueryRewriteConfig.disabled())
                .build();

        new QueryRewriteNode(gateway, new PromptTemplateRenderer())
                .process(context);

        assertEquals("它的续航怎么样", context.retrievalQuery());
    }

    @Test
    void shouldFallbackToOriginalQueryWhenModelFails() {
        ChatCompletionGateway gateway = command ->
                Mono.error(new IllegalStateException("provider unavailable"));
        ModelChatContext context = enabledContext(
                true,
                new AtomicInteger());

        new QueryRewriteNode(gateway, new PromptTemplateRenderer())
                .process(context);

        assertEquals("它的续航怎么样", context.retrievalQuery());
    }

    @Test
    void shouldStopChainWhenFallbackIsDisabled() {
        ChatCompletionGateway gateway = command ->
                Mono.just(new ChatCompletionResult("", TokenUsage.empty()));
        ModelChatContext context = enabledContext(
                false,
                new AtomicInteger());

        assertThrows(
                BusinessException.class,
                () -> new QueryRewriteNode(
                        gateway,
                        new PromptTemplateRenderer())
                        .process(context));
    }

    private static ModelChatContext enabledContext(boolean fallbackToOriginal, AtomicInteger totalTokens) {
        return ModelChatContext.builder()
                .runtimeVariables(variables())
                .queryRewriteConfig(QueryRewriteConfig.builder()
                        .enabled(true)
                        .modelConfig(ChatModelRuntimeConfig.builder()
                                .instanceId(99L)
                                .build())
                        .prompt(new PromptRuntimeSnapshot(
                                88L,
                                """
                                        只输出一个可独立检索的问题。
                                        历史：{{query.rewrite_history}}
                                        当前问题：{{user.query}}
                                        """,
                                List.of(
                                        definition(
                                                "query.rewrite_history",
                                                "MESSAGE_LIST"),
                                        definition(
                                                "user.query",
                                                "STRING")),
                                PromptRole.SYSTEM))
                        .maxHistoryRounds(4)
                        .maxHistoryTokens(1000)
                        .maxOutputCharacters(200)
                        .timeoutMillis(1000)
                        .fallbackToOriginal(fallbackToOriginal)
                        .build())
                .modelUsageRecorder(usage -> totalTokens.addAndGet(java.util.Objects.requireNonNullElse(usage.totalTokens(), 0)))
                .build();
    }

    private static PromptRuntimeVariableDefinition definition(String name, String type) {
        return new PromptRuntimeVariableDefinition(name, type, true);
    }

    private static RuntimeVariables variables() {
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(
                RuntimeVariableAccessors.USER_QUERY,
                "它的续航怎么样");
        variables.put(
                RuntimeVariableAccessors.QUERY_REWRITE_HISTORY,
                List.of(
                        new ChatHistoryMessage(
                                ChatHistoryMessage.Role.USER,
                                "介绍一下 iPhone 16 Pro"),
                        new ChatHistoryMessage(
                                ChatHistoryMessage.Role.ASSISTANT,
                                "它是苹果的高端手机")));
        return variables;
    }
}
