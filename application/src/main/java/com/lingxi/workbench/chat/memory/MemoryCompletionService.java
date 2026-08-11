package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.memory.config.MemoryChatModelResolver;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 使用发布 PromptVersion 和指定 Chat 模型执行记忆提取任务。 */
@Service
@RequiredArgsConstructor
public class MemoryCompletionService {

    private static final Duration COMPLETION_TIMEOUT = Duration.ofSeconds(30);
    /** 记忆模板保留自己的任务变量，例如 conversation_history 和 existing_profile。 */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\{\\{\\s*([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*)\\s*}}");

    private final PromptRuntimeService promptRuntimeService;
    private final MemoryChatModelResolver chatModelResolver;
    private final ChatCompletionGateway completionGateway;

    /**
     * 解析已发布 PromptVersion、替换变量、调用 Chat 模型获取提取结果。
     *
     * @param promptVersionId   已发布 PromptVersion 主键
     * @param chatModelInstanceId Chat 模型实例主键
     * @param variables         变量名到值的映射，键不含 {@code {{}} 包裹}
     * @return 模型返回的提取结果（非空）
     * @throws BusinessException Prompt 角色非 SYSTEM 或存在未解析变量
     * @throws IllegalStateException 模型返回空内容
     */
    public String complete(Long promptVersionId, Long chatModelInstanceId, Map<String, String> variables) {
        PromptRuntimeSnapshot snapshot = promptRuntimeService.resolvePublished(promptVersionId);
        if (snapshot.role() != PromptRole.SYSTEM) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "记忆提取Prompt必须为SYSTEM角色");
        }
        String prompt = renderPrompt(snapshot.content(), variables);
        ChatModelRuntimeConfig chatConfig = chatModelResolver.resolve(chatModelInstanceId);
        ChatCompletionCommand command = new ChatCompletionCommand(prompt, "", List.of(), chatConfig);
        ChatCompletionResult<String> result = Objects.requireNonNull(
                completionGateway.complete(command).block(COMPLETION_TIMEOUT),
                "记忆提取模型未返回结果");
        if (result.content() == null || result.content().isBlank()) {
            throw new IllegalStateException("记忆提取模型返回空内容");
        }
        return result.content().trim();
    }

    /**
     * 只解析模板正文原有的变量，避免把用户对话中包含的双花括号误判成未解析变量。
     */
    private String renderPrompt(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder(template.length());
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!variables.containsKey(variableName)) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "记忆提取Prompt存在未解析变量：" + variableName);
            }
            String replacement = Objects.requireNonNullElse(variables.get(variableName), "");
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
