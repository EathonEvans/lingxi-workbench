package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.ContextualizationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** 在共享上下文准备阶段把依赖历史的追问重写为可独立路由的问题。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRewriter {

    private static final String REWRITE_REQUEST = "请严格执行系统中的问题重写任务。";
    private static final int MIN_REASONABLE_REWRITE_CHARACTERS = 128;
    private static final int MAX_REWRITE_EXPANSION_MULTIPLIER = 4;
    private static final Pattern SOCIAL_TURN_PATTERN = Pattern.compile(
            "^(?:你好|您好|嗨|哈喽|hello|hi|在吗|谢谢|感谢|好的|好|收到|明白了|再见)[！!。,.，?？~～\\s]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("```|\\*\\*|__");

    private final PromptRuntimeService promptRuntimeService;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final ChatCompletionGateway completionGateway;
    private final PromptTemplateRenderer promptTemplateRenderer;

    public RewriteResult rewrite(ContextualizationPolicy policy, String originalQuery, RuntimeVariables variables, List<ChatMemoryMessageBO> recentMessages) {
        if (!policy.isEnabled() || recentMessages.isEmpty() || isStandaloneSocialTurn(originalQuery)) {
            return RewriteResult.unchanged(originalQuery);
        }
        TokenUsage usage = TokenUsage.empty();
        int modelCalls = 0;
        try {
            List<ChatHistoryMessage> history = toHistory(recentMessages);
            variables.put(RuntimeVariableAccessors.CONTEXTUALIZATION_HISTORY, history);

            PromptRuntimeSnapshot prompt = promptRuntimeService.resolvePublished(policy.getPromptVersionId());
            String systemPrompt = promptTemplateRenderer.renderQueryRewritePrompt(prompt, variables).trim();

            ChatModelRuntimeConfig modelConfig = modelRuntimeConfigService.getChatModelConfig(policy.getChatModelInstanceId());
            modelConfig.setMaxTokens(policy.effectiveMaxOutputTokens());

            // 当前问题和历史已由发布 Prompt 变量注入，禁止再按消息角色重复发送同一份上下文。
            ChatCompletionCommand command = new ChatCompletionCommand(REWRITE_REQUEST, systemPrompt, List.of(), modelConfig);
            modelCalls = 1;
            ChatCompletionResult<String> result = Objects.requireNonNull(
                    completionGateway.complete(command).block(Duration.ofSeconds(policy.effectiveTimeoutSeconds())),
                    "问题重写模型未返回结果");
            usage = result.usage();
            return new RewriteResult(validateResult(result, policy, originalQuery), usage, modelCalls);
        } catch (InvalidOutputException exception) {
            if (!policy.isFallbackToOriginal()) {
                throw rewriteFailure(exception);
            }
            log.warn("问题重写输出未通过校验，按ApplicationVersion策略回退原问题。modelInstanceId={}, promptVersionId={}, reason={}",
                    policy.getChatModelInstanceId(), policy.getPromptVersionId(), exception.getMessage());
            return new RewriteResult(originalQuery, usage, modelCalls);
        } catch (RuntimeException exception) {
            if (hasInterruptedCause(exception)) {
                // 用户取消或应用关闭的线程中断不是问题重写失败，不允许回退后继续执行。
                throw exception;
            }
            if (!policy.isFallbackToOriginal()) {
                throw rewriteFailure(exception);
            }
            log.warn("问题重写失败，按ApplicationVersion策略回退原问题。modelInstanceId={}, promptVersionId={}",
                    policy.getChatModelInstanceId(), policy.getPromptVersionId(), exception);
            return new RewriteResult(originalQuery, usage, modelCalls);
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

    private static String validateResult(ChatCompletionResult<String> result, ContextualizationPolicy policy, String originalQuery) {
        String rawResult = Objects.requireNonNullElse(result.content(), "").trim();
        if (rawResult.isBlank()) {
            throw new InvalidOutputException(emptyResultReason(result));
        }
        if ("LENGTH".equalsIgnoreCase(result.finishReason())) {
            throw new InvalidOutputException("问题重写结果因达到模型输出上限而被截断");
        }
        if (rawResult.contains("\n") || rawResult.contains("\r")) {
            throw new InvalidOutputException("问题重写结果必须为单行用户请求");
        }
        if (MARKDOWN_PATTERN.matcher(rawResult).find()) {
            throw new InvalidOutputException("问题重写结果不得包含Markdown格式");
        }
        String rewrittenQuery = rawResult.replaceAll("\\s+", " ");
        if (rewrittenQuery.length() > policy.effectiveMaxOutputCharacters()) {
            throw new InvalidOutputException("问题重写结果超过字符上限");
        }
        int reasonableCharacters = Math.max(
                MIN_REASONABLE_REWRITE_CHARACTERS,
                originalQuery.trim().length() * MAX_REWRITE_EXPANSION_MULTIPLIER);
        if (rewrittenQuery.length() > reasonableCharacters) {
            throw new InvalidOutputException("问题重写结果相对原问题扩张过度，疑似回答了用户问题");
        }
        return rewrittenQuery;
    }

    /** 寒暄和确认类输入本身可独立理解，不应消耗一次模型调用。 */
    private static boolean isStandaloneSocialTurn(String query) {
        return SOCIAL_TURN_PATTERN.matcher(Objects.requireNonNullElse(query, "").trim()).matches();
    }

    /** 空正文只记录生成元数据，禁止把推理正文写入日志。 */
    private static String emptyResultReason(ChatCompletionResult<String> result) {
        String finishReason = Optional.ofNullable(result.finishReason()).filter(value -> !value.isBlank()).orElse("UNKNOWN");
        Integer outputTokens = result.usage().outputTokens();
        return "问题重写结果为空，finishReason=" + finishReason + "，outputTokens=" + outputTokens
                + "，reasoningCharacters=" + result.reasoningCharacters();
    }

    private static BusinessException rewriteFailure(RuntimeException cause) {
        BusinessException failure = new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "问题重写失败，请稍后重试");
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
     * @param query      模型校验后的独立问题，失败回退时为原问题
     * @param usage      本次问题重写模型调用的 Token 用量
     * @param modelCalls 已实际发起的模型调用次数
     */
    public record RewriteResult(String query, TokenUsage usage, int modelCalls) {

        private static RewriteResult unchanged(String query) {
            return new RewriteResult(query, TokenUsage.empty(), 0);
        }
    }

    /** 标记模型已响应但正文不符合问题重写输出约束。 */
    private static final class InvalidOutputException extends RuntimeException {

        private InvalidOutputException(String message) {
            super(message);
        }
    }
}
