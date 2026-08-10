package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 基于 Spring AI 的 OpenAI 兼容协议非流式调用实现。 */
@Component
@RequiredArgsConstructor
public class SpringAiChatCompletionGateway implements ChatCompletionGateway {

    private final OpenAiCompatibleChatModelFactory chatModelFactory;
    private final ObservationRegistry observationRegistry;

    /**
     * 中间任务使用自身的 System Prompt，不追加面向最终回答的 Markdown 输出约束。
     */
    @Override
    public Mono<ChatCompletionResult> complete(ChatCompletionCommand command) {
        Observation parent = observationRegistry.getCurrentObservation();
        return Mono.fromCallable(() -> callWithParent(command, null, parent)).subscribeOn(Schedulers.boundedElastic());
    }

    /** Planner 等受控任务使用兼容范围更广的 JSON Object 响应模式。 */
    @Override
    public Mono<ChatCompletionResult> completeJson(ChatCompletionCommand command) {
        Observation parent = observationRegistry.getCurrentObservation();
        return Mono.fromCallable(() -> callWithParent(command, OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT, parent))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 在实际模型线程恢复调用方 Observation，使 Spring AI 模型 span 正确挂到业务链路下。 */
    private ChatCompletionResult callWithParent(ChatCompletionCommand command, OpenAiChatModel.ResponseFormat.Type responseType,
                                                Observation parent) {
        if (Objects.isNull(parent)) {
            return call(command, responseType);
        }
        try (Observation.Scope ignored = parent.openScope()) {
            return call(command, responseType);
        }
    }

    private ChatCompletionResult call(ChatCompletionCommand command, OpenAiChatModel.ResponseFormat.Type responseType) {
        OpenAiChatModel chatModel = chatModelFactory.create(command.modelConfig());
        List<Message> messages = new ArrayList<>();
        if (!command.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(command.systemPrompt()));
        }
        command.history().stream()
                .map(SpringAiChatCompletionGateway::toOpenAiMessage)
                .forEach(messages::add);
        messages.add(new UserMessage(command.prompt()));
        Prompt prompt = Optional.ofNullable(responseType)
                .map(type -> jsonPrompt(messages, chatModel.getOptions(), type))
                .orElseGet(() -> new Prompt(messages));
        return toResult(chatModel.call(prompt));
    }

    private static Prompt jsonPrompt(List<Message> messages, OpenAiChatOptions modelOptions,
                                     OpenAiChatModel.ResponseFormat.Type responseType) {
        OpenAiChatModel.ResponseFormat responseFormat = OpenAiChatModel.ResponseFormat.builder()
                .type(responseType)
                .build();
        // Prompt级 options 不会自动继承 ChatModel 默认值，必须复制后再追加响应格式。
        OpenAiChatOptions options = modelOptions.mutate()
                .responseFormat(responseFormat)
                .build();
        return new Prompt(messages, options);
    }

    private static Message toOpenAiMessage(ChatHistoryMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    private static ChatCompletionResult toResult(ChatResponse response) {
        if (Objects.isNull(response)) {
            return new ChatCompletionResult("", TokenUsage.empty());
        }
        String content = Optional.ofNullable(response.getResult())
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse("");
        Usage usage = response.getMetadata().getUsage();
        return new ChatCompletionResult(content, TokenUsageConverter.convert(usage));
    }
}
