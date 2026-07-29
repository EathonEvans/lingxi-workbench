package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 基于 Spring AI 的 OpenAI 兼容协议非流式调用实现。 */
@Component
@RequiredArgsConstructor
public class SpringAiChatCompletionGateway implements ChatCompletionGateway {

    private final OpenAiCompatibleChatModelFactory chatModelFactory;

    /**
     * 中间任务使用自身的 System Prompt，不追加面向最终回答的 Markdown 输出约束。
     */
    @Override
    public Mono<ChatCompletionResult> complete(ChatCompletionCommand command) {
        return Mono.fromCallable(() -> call(command)).subscribeOn(Schedulers.boundedElastic());
    }

    private ChatCompletionResult call(ChatCompletionCommand command) {
        OpenAiChatModel chatModel = chatModelFactory.create(command.modelConfig());
        List<Message> messages = new ArrayList<>();
        if (!command.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(command.systemPrompt()));
        }
        command.history().stream()
                .map(SpringAiChatCompletionGateway::toOpenAiMessage)
                .forEach(messages::add);
        messages.add(new UserMessage(command.prompt()));
        return toResult(chatModel.call(new Prompt(messages)));
    }

    private static Message toOpenAiMessage(ChatHistoryMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    private static ChatCompletionResult toResult(ChatResponse response) {
        if (Objects.isNull(response)) {
            return new ChatCompletionResult("", null, null, null);
        }
        String content = "";
        if (response.getResult() != null) {
            Generation result = response.getResult();
            content = Objects.requireNonNullElse(result.getOutput().getText(), "");
        }
        Usage usage = response.getMetadata().getUsage();
        return new ChatCompletionResult(
                content,
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens());
    }
}
