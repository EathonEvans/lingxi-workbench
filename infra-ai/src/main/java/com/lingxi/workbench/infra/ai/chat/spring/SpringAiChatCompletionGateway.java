package com.lingxi.workbench.infra.ai.chat.spring;

import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiChatModelFactory;
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

/** Spring AI 的 OpenAI 兼容非 Agent 调用实现，只服务改写、记忆等基础设施场景。 */
@Component
@RequiredArgsConstructor
public class SpringAiChatCompletionGateway implements ChatCompletionGateway {

    private final OpenAiChatModelFactory chatModelFactory;
    private final SpringAiChatClientFactory chatClientFactory;
    private final ObservationRegistry observationRegistry;

    @Override
    public Mono<ChatCompletionResult<String>> complete(ChatCompletionCommand command) {
        Observation parent = observationRegistry.getCurrentObservation();
        return Mono.fromCallable(() -> callWithParent(command, parent)).subscribeOn(Schedulers.boundedElastic());
    }

    /** 在模型线程恢复调用方 Observation，使供应商 span 归属当前业务链路。 */
    private ChatCompletionResult<String> callWithParent(ChatCompletionCommand command, Observation parent) {
        if (parent == null) {
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
        command.history().stream().map(SpringAiChatCompletionGateway::toOpenAiMessage).forEach(messages::add);
        messages.add(new UserMessage(command.prompt()));
        ChatClient chatClient = chatClientFactory.create(chatModel);
        ChatResponse response = chatClient.prompt(new Prompt(messages))
                // 非 Agent 网关不得隐式进入 Spring AI Tool 循环。
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
        if (response == null) {
            return new ChatCompletionResult<>("", TokenUsage.empty());
        }
        Generation generation = response.getResult();
        String content = Optional.ofNullable(generation).map(Generation::getOutput)
                .map(AbstractMessage::getText).orElse("");
        String finishReason = Optional.ofNullable(generation).map(Generation::getMetadata)
                .map(metadata -> metadata.getFinishReason()).orElse(null);
        int reasoningCharacters = Optional.ofNullable(generation).map(Generation::getOutput)
                .map(AbstractMessage::getMetadata).map(metadata -> metadata.get("reasoningContent"))
                .filter(String.class::isInstance).map(String.class::cast).map(String::length).orElse(0);
        Usage usage = response.getMetadata().getUsage();
        return new ChatCompletionResult<>(content, SpringAiTokenUsageConverter.convert(usage), finishReason,
                reasoningCharacters);
    }
}
