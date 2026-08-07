package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.memory.config.MemoryChatModelResolver;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.workflow.model.ContextualizationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 在共享上下文准备阶段把依赖历史的追问改写为可独立路由的问题。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryContextualizer {

    private final PromptRuntimeService promptRuntimeService;
    private final MemoryChatModelResolver chatModelResolver;
    private final ChatCompletionGateway completionGateway;
    private final PromptTemplateRenderer promptTemplateRenderer;

    public ContextualizationResult contextualize(ContextualizationPolicy policy, String originalQuery, RuntimeVariables variables, List<ChatMemoryMessageBO> recentMessages) {
        if (!policy.isEnabled()) {
            return new ContextualizationResult(originalQuery, TokenUsage.empty());
        }
        TokenUsage usage = TokenUsage.empty();
        try {
            List<ChatHistoryMessage> history = toHistory(recentMessages);
            variables.put(RuntimeVariableAccessors.QUERY_REWRITE_HISTORY, history);
            PromptRuntimeSnapshot prompt = promptRuntimeService.resolvePublished(policy.getPromptVersionId());
            String systemPrompt = promptTemplateRenderer.renderQueryRewritePrompt(prompt, variables).trim();
            ChatModelRuntimeConfig modelConfig = chatModelResolver.resolve(policy.getChatModelInstanceId());
            modelConfig.setMaxTokens(policy.effectiveMaxOutputTokens());
            ChatCompletionCommand command = new ChatCompletionCommand(originalQuery, systemPrompt, history, modelConfig);
            ChatCompletionResult result = Objects.requireNonNull(
                    completionGateway.complete(command).block(Duration.ofSeconds(policy.effectiveTimeoutSeconds())),
                    "上下文化模型未返回结果");
            usage = result.usage();
            return new ContextualizationResult(validateResult(result.content(), policy), usage);
        } catch (RuntimeException exception) {
            if (!policy.isFallbackToOriginal()) {
                BusinessException failure = new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "问题上下文化失败，请稍后重试");
                failure.initCause(exception);
                throw failure;
            }
            log.warn("问题上下文化失败，按ApplicationVersion策略回退原问题。modelInstanceId={}, promptVersionId={}",
                    policy.getChatModelInstanceId(), policy.getPromptVersionId(), exception);
            return new ContextualizationResult(originalQuery, usage);
        }
    }

    private static String validateResult(String content, ContextualizationPolicy policy) {
        String contextualized = Objects.requireNonNullElse(content, "").replaceAll("\\s+", " ").trim();
        if (contextualized.isBlank()) {
            throw new IllegalStateException("上下文化结果为空");
        }
        if (contextualized.length() > policy.effectiveMaxOutputCharacters()) {
            throw new IllegalStateException("上下文化结果超过字符上限");
        }
        if (contextualized.contains("```")) {
            throw new IllegalStateException("上下文化结果包含代码围栏");
        }
        return contextualized;
    }

    private static List<ChatHistoryMessage> toHistory(List<ChatMemoryMessageBO> messages) {
        return messages.stream()
                .map(message -> new ChatHistoryMessage(switch (message.role()) {
                    case USER -> ChatHistoryMessage.Role.USER;
                    case ASSISTANT -> ChatHistoryMessage.Role.ASSISTANT;
                }, message.content()))
                .toList();
    }

    /**
     * @param query 模型校验后的独立问题，失败回退时为原问题
     * @param usage 本次上下文化模型调用的 Token 用量
     */
    public record ContextualizationResult(String query, TokenUsage usage) {
    }
}
