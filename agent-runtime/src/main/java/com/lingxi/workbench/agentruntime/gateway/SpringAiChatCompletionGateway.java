package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Spring AI 的 OpenAI 兼容协议非流式调用实现。
 */
@Component
@RequiredArgsConstructor
public class SpringAiChatCompletionGateway implements ChatCompletionGateway {

    private final OpenAiCompatibleChatModelFactory chatModelFactory;
    private final SpringAiChatClientFactory chatClientFactory;
    private final ObservationRegistry observationRegistry;

    /**
     * 中间任务使用自身的 System Prompt，不追加面向最终回答的 Markdown 输出约束。
     */
    @Override
    public Mono<ChatCompletionResult<String>> complete(ChatCompletionCommand command) {
        Observation parent = observationRegistry.getCurrentObservation();
        return Mono.fromCallable(() -> callWithParent(command, parent)).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 在实际模型线程恢复调用方 Observation，使 Spring AI 模型 span 正确挂到业务链路下。
     */
    private ChatCompletionResult<String> callWithParent(ChatCompletionCommand command, Observation parent) {
        if (Objects.isNull(parent)) {
            return call(command);
        }
        try (Observation.Scope ignored = parent.openScope()) {
            return call(command);
        }
    }

    private ChatCompletionResult<String> call(ChatCompletionCommand command) {
        OpenAiChatModel chatModel = chatModelFactory.create(command.modelConfig());
        List<Message> messages = new ArrayList<>();
        if (!command.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(command.systemPrompt()));
        }
        command.history().stream()
                .map(SpringAiChatCompletionGateway::toOpenAiMessage)
                .forEach(messages::add);
        messages.add(new UserMessage(command.prompt()));
        Prompt prompt = new Prompt(messages);
        ChatClient chatClient = chatClientFactory.create(chatModel);
        ChatResponse response = chatClient.prompt(prompt)
                // 工具循环由 Lingxi 执行层负责，禁止 ChatClient 在网关内隐式执行工具。
                .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                .call()
                .chatResponse();
        return toResult(response);
    }

    private static Message toOpenAiMessage(ChatHistoryMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    private static ChatCompletionResult<String> toResult(ChatResponse response) {
        if (Objects.isNull(response)) {
            return new ChatCompletionResult<>("", TokenUsage.empty());
        }
        Generation generation = response.getResult();
        String content = Optional.ofNullable(generation)
                .map(Generation::getOutput)
                .map(AbstractMessage::getText)
                .orElse("");
        String finishReason = Optional.ofNullable(generation)
                .map(Generation::getMetadata)
                .map(metadata -> metadata.getFinishReason())
                .orElse(null);
        int reasoningCharacters = Optional.ofNullable(generation)
                .map(Generation::getOutput)
                .map(AbstractMessage::getMetadata)
                .map(metadata -> metadata.get("reasoningContent"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::length)
                .orElse(0);
        Usage usage = response.getMetadata().getUsage();
        return new ChatCompletionResult<>(content, TokenUsageConverter.convert(usage), finishReason, reasoningCharacters);
    }
}
