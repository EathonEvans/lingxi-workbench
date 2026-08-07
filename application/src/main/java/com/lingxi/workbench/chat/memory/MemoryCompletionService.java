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

/** 使用发布 PromptVersion 和指定 Chat 模型执行记忆提取任务。 */
@Service
@RequiredArgsConstructor
public class MemoryCompletionService {

    private static final Duration COMPLETION_TIMEOUT = Duration.ofSeconds(30);

    private final PromptRuntimeService promptRuntimeService;
    private final MemoryChatModelResolver chatModelResolver;
    private final ChatCompletionGateway completionGateway;

    public String complete(Long promptVersionId, Long chatModelInstanceId, Map<String, String> variables) {
        PromptRuntimeSnapshot snapshot = promptRuntimeService.resolvePublished(promptVersionId);
        if (snapshot.role() != PromptRole.SYSTEM) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "记忆提取Prompt必须为SYSTEM角色");
        }
        String prompt = snapshot.content();
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            prompt = prompt.replace("{{" + variable.getKey() + "}}", variable.getValue());
        }
        if (prompt.contains("{{") || prompt.contains("}}")) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "记忆提取Prompt存在未解析变量");
        }
        ChatModelRuntimeConfig chatConfig = chatModelResolver.resolve(chatModelInstanceId);
        ChatCompletionCommand command = new ChatCompletionCommand(prompt, "", List.of(), chatConfig);
        ChatCompletionResult result = Objects.requireNonNull(
                completionGateway.complete(command).block(COMPLETION_TIMEOUT),
                "记忆提取模型未返回结果");
        if (result.content() == null || result.content().isBlank()) {
            throw new IllegalStateException("记忆提取模型返回空内容");
        }
        return result.content().trim();
    }
}
