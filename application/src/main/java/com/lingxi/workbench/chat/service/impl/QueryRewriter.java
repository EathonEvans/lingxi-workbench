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

/**
 * 问题重写组件：在共享上下文准备阶段，把依赖会话历史的追问重写为可独立路由的问题。
 *
 * <p>会话中的追问（如“那第二个方案呢”）只有结合前文才能被下游理解和路由，本组件在进入主模型
 * 调用之前，先让重写模型基于近期会话历史生成一个自包含、可独立路由的问题。</p>
 *
 * <p>重写结果必须通过严格输出校验（单行、无 Markdown、长度受限、不得过度扩张），校验失败或模型
 * 调用异常时按 {@link ContextualizationPolicy} 决定抛错还是回退原问题；寒暄等本身可独立理解的
 * 输入直接跳过重写，不消耗模型调用。</p>
 */
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

    /**
     * 按上下文策略重写原始问题，返回可独立路由的问题。
     *
     * <p>策略未开启、无历史消息或输入本身是可独立理解的寒暄时直接返回原问题；否则注入会话历史、
     * 渲染发布版 Prompt 并调用重写模型，结果必须通过输出校验。校验失败或模型调用异常时按策略
     * 决定抛错还是回退原问题，线程中断（用户取消/应用关闭）场景一律直接上抛。</p>
     *
     * @param policy         上下文策略，控制重写开关、模型实例、Prompt 版本、超时与回退行为
     * @param originalQuery  用户本次输入的原始问题
     * @param variables      共享上下文变量，方法内会注入重写所需的会话历史
     * @param recentMessages 近期会话消息，作为重写的背景上下文
     * @return 重写结果，包含校验通过的问题（或回退的原问题）、Token 用量与模型调用次数
     */
    public RewriteResult rewrite(ContextualizationPolicy policy, String originalQuery, RuntimeVariables variables, List<ChatMemoryMessageBO> recentMessages) {
        // 策略未开启、无历史可参考或输入本身是可独立理解的寒暄时，直接返回原问题，不发起模型调用。
        if (!policy.isEnabled() || recentMessages.isEmpty() || isStandaloneSocialTurn(originalQuery)) {
            return RewriteResult.unchanged(originalQuery);
        }
        TokenUsage usage = TokenUsage.empty();
        int modelCalls = 0;
        try {
            // 将近期会话历史注入上下文变量，供发布版 Prompt 渲染使用；历史是重写所需的全部背景。
            List<ChatHistoryMessage> history = toHistory(recentMessages);
            variables.put(RuntimeVariableAccessors.CONTEXTUALIZATION_HISTORY, history);

            // 仅使用发布版 Prompt 渲染系统提示词，避免开发中的草稿版本影响线上重写行为。
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
            // 模型已响应但正文未通过输出校验：按策略决定上抛失败或回退原问题继续执行。
            if (!policy.isFallbackToOriginal()) {
                throw rewriteFailure(exception);
            }
            log.warn("问题重写输出未通过校验，按ApplicationVersion策略回退原问题。modelInstanceId={}, promptVersionId={}, reason={}",
                    policy.getChatModelInstanceId(), policy.getPromptVersionId(), exception.getMessage());
            return new RewriteResult(originalQuery, usage, modelCalls);
        } catch (RuntimeException exception) {
            // 模型调用异常按策略处理：线程中断属于用户主动取消或应用关闭，直接上抛；其余异常可回退原问题。
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

    /**
     * 判断异常链中是否包含线程中断异常。
     *
     * <p>沿 cause 链逐层检查，避免中断被 reactor 包装或业务代码包裹后丢失语义。</p>
     *
     * @param exception 待检查的异常
     * @return 异常链中存在 {@link InterruptedException} 时返回 true
     */
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

    /**
     * 校验模型重写结果，返回清洗后的独立问题。
     *
     * <p>依次拦截空正文、截断（finishReason=LENGTH）、多行输出、Markdown 格式、超长与过度扩张等
     * 不符合输出约束的结果，任一不满足即抛出 {@link InvalidOutputException}，由调用方按策略处理。</p>
     *
     * @param result        重写模型的完成结果
     * @param policy        上下文策略，提供输出长度上限等约束
     * @param originalQuery 原始问题，用于计算合理的重写扩张上限
     * @return 空白折叠后的重写问题
     */
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
        // 将连续空白折叠为单个空格，统一格式后再参与长度与扩张度校验。
        String rewrittenQuery = rawResult.replaceAll("\\s+", " ");
        if (rewrittenQuery.length() > policy.effectiveMaxOutputCharacters()) {
            throw new InvalidOutputException("问题重写结果超过字符上限");
        }
        // 重写结果相对原问题过度扩张，说明模型可能直接回答了问题而非重写，按原问题长度与固定下限双重约束拦截。
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

    /**
     * 将重写失败包装为面向用户的业务异常并保留原始原因。
     *
     * @param cause 底层失败原因（输出校验失败或模型调用异常）
     * @return 统一的“问题重写失败”业务异常
     */
    private static BusinessException rewriteFailure(RuntimeException cause) {
        BusinessException failure = new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "问题重写失败，请稍后重试");
        failure.initCause(cause);
        return failure;
    }

    /**
     * 将内存消息转换为模型可消费的历史消息列表。
     *
     * <p>仅保留角色与正文；角色映射使用穷尽 switch，新增消息角色时编译期强制补齐映射，避免静默丢失。</p>
     *
     * @param messages 内存中的会话消息
     * @return 角色映射后的模型历史消息列表
     */
    private static List<ChatHistoryMessage> toHistory(List<ChatMemoryMessageBO> messages) {
        return messages.stream()
                // 角色映射采用穷尽 switch，新增消息角色时编译期强制补齐映射。
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

        /**
         * 构造不经过模型调用的原样结果，Token 用量为空、模型调用次数为 0。
         *
         * @param query 原问题
         * @return 标记为未重写的重写结果
         */
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
