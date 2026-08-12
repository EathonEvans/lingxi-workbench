package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
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
            return new ContextualizationResult(originalQuery, TokenUsage.empty(), 0);
        }
        TokenUsage usage = TokenUsage.empty();
        int modelCalls = 0;
        try {
            List<ChatHistoryMessage> history = toHistory(recentMessages);
            variables.put(RuntimeVariableAccessors.CONTEXTUALIZATION_HISTORY, history);

            PromptRuntimeSnapshot prompt = promptRuntimeService.resolvePublished(policy.getPromptVersionId());
            String systemPrompt = promptTemplateRenderer.renderContextualizationPrompt(prompt, variables).trim();

            ChatModelRuntimeConfig modelConfig = chatModelResolver.resolve(policy.getChatModelInstanceId());
            modelConfig.setMaxTokens(policy.effectiveMaxOutputTokens());

            ChatCompletionCommand command = new ChatCompletionCommand(originalQuery, systemPrompt, history, modelConfig);
            modelCalls = 1;
            ChatCompletionResult<String> result = Objects
                    .requireNonNull(completionGateway.complete(command).block(Duration.ofSeconds(policy.effectiveTimeoutSeconds())),
                    "上下文化模型未返回结果");

            usage = result.usage();
            return new ContextualizationResult(validateResult(result.content(), policy), usage, modelCalls);
        } catch (InvalidOutputException exception) {
            if (!policy.isFallbackToOriginal()) {
                throw contextualizationFailure(exception);
            }
            log.warn("问题上下文化输出未通过校验，按ApplicationVersion策略回退原问题。modelInstanceId={}, promptVersionId={}, reason={}",
                    policy.getChatModelInstanceId(), policy.getPromptVersionId(), exception.getMessage());
            return new ContextualizationResult(originalQuery, usage, modelCalls);
        } catch (RuntimeException exception) {
            if (hasInterruptedCause(exception)) {
                // 用户取消或应用关闭的线程中断不是上下文化失败，不允许回退后继续执行。
                throw exception;
            }
            if (!policy.isFallbackToOriginal()) {
                throw contextualizationFailure(exception);
            }
            log.warn("问题上下文化失败，按ApplicationVersion策略回退原问题。modelInstanceId={}, promptVersionId={}",
                    policy.getChatModelInstanceId(), policy.getPromptVersionId(), exception);
            return new ContextualizationResult(originalQuery, usage, modelCalls);
        }
    }

    private static boolean hasInterruptedCause(Throwable exception) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String validateResult(String content, ContextualizationPolicy policy) {
        String contextualized = Objects.requireNonNullElse(content, "").replaceAll("\\s+", " ").trim();
        if (contextualized.isBlank()) {
            throw new InvalidOutputException("上下文化结果为空");
        }
        if (contextualized.length() > policy.effectiveMaxOutputCharacters()) {
            throw new InvalidOutputException("上下文化结果超过字符上限");
        }
        if (contextualized.contains("```")) {
            throw new InvalidOutputException("上下文化结果包含代码围栏");
        }
        return contextualized;
    }

    private static BusinessException contextualizationFailure(RuntimeException cause) {
        BusinessException failure = new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "问题上下文化失败，请稍后重试");
        failure.initCause(cause);
        return failure;
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
     * @param modelCalls 已实际发起的模型调用次数
     */
    public record ContextualizationResult(String query, TokenUsage usage, int modelCalls) {
    }

    /** 标记模型已响应但正文不符合上下文化输出约束。 */
    private static final class InvalidOutputException extends RuntimeException {

        private InvalidOutputException(String message) {
            super(message);
        }
    }
}
