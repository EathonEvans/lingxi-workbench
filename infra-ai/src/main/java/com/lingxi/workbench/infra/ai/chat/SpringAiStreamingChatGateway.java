package com.lingxi.workbench.infra.ai.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/** 基于 Spring AI 的 OpenAI 兼容协议流式对话实现。 */
@Component
@RequiredArgsConstructor
public class SpringAiStreamingChatGateway implements StreamingChatGateway {

    /** 统一约束模型输出为可直接渲染的 Markdown。 */
    private static final String SYSTEM_PROMPT = """
            你是灵析通用助手。请优先使用结构清晰、可直接渲染的 Markdown 回答。
            当用户要求展示 Markdown 表格时，不要使用代码围栏包裹表格；表头、分隔行和数据行必须连续，且每行列数保持一致。
            只有用户明确要求查看 Markdown 源码时，才使用 markdown 代码围栏。
            """;

    private final OpenAiCompatibleChatModelFactory chatModelFactory;

    @Override
    public Flux<ChatStreamChunk> stream(ChatStreamCommand command) {
        OpenAiChatModel chatModel = chatModelFactory.create(command.modelConfig());
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        command.history().stream()
                .map(SpringAiStreamingChatGateway::toSpringAiMessage)
                .forEach(messages::add);
        messages.add(new UserMessage(command.prompt()));
        Prompt prompt = new Prompt(messages);

        return chatModel.stream(prompt)
                .map(SpringAiStreamingChatGateway::toChunk)
                .filter(chunk -> !chunk.content().isEmpty() || chunk.hasUsage());
    }

    private static Message toSpringAiMessage(ChatHistoryMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    private static ChatStreamChunk toChunk(ChatResponse response) {
        if (response == null) {
            return new ChatStreamChunk("");
        }
        String content = response.getResult() == null
                ? ""
                : response.getResult().getOutput().getText();
        Usage usage = response.getMetadata() == null
                ? null
                : response.getMetadata().getUsage();
        if (!hasActualUsage(usage)) {
            usage = null;
        }
        return new ChatStreamChunk(
                content == null ? "" : content,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens());
    }

    /** Spring AI 普通流式分片中的空 Usage 会返回全 0，需要与最终真实用量区分。 */
    private static boolean hasActualUsage(Usage usage) {
        if (usage == null) {
            return false;
        }
        return positive(usage.getPromptTokens())
                || positive(usage.getCompletionTokens())
                || positive(usage.getTotalTokens());
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
