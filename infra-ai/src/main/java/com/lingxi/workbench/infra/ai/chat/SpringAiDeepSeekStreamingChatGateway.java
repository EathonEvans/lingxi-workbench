package com.lingxi.workbench.infra.ai.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 基于 Spring AI 的 DeepSeek 流式对话实现。
 */
@Component
@RequiredArgsConstructor
public class SpringAiDeepSeekStreamingChatGateway implements StreamingChatGateway {

    private final DeepSeekChatModel chatModel;

    @Override
    public Flux<ChatStreamChunk> stream(ChatStreamCommand command) {
        Prompt prompt = new Prompt(new UserMessage(command.prompt()));
        return chatModel.stream(prompt)
                .map(SpringAiDeepSeekStreamingChatGateway::toChunk)
                .filter(chunk -> !chunk.content().isEmpty());
    }

    private static ChatStreamChunk toChunk(ChatResponse response) {
        if (response == null) {
            return new ChatStreamChunk("");
        } else {
            response.getResult();
        }
        String content = response.getResult().getOutput().getText();
        return new ChatStreamChunk(content == null ? "" : content);
    }
}
