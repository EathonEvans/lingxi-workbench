package com.lingxi.workbench.infra.ai.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/** 基于 Spring AI 的 OpenAI 兼容协议流式对话实现。 */
@Component
@RequiredArgsConstructor
public class SpringAiOpenAiCompatibleStreamingChatGateway implements StreamingChatGateway {

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
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(command.prompt())
        ));

        return chatModel.stream(prompt)
                .map(SpringAiOpenAiCompatibleStreamingChatGateway::toChunk)
                .filter(chunk -> !chunk.content().isEmpty());
    }

    private static ChatStreamChunk toChunk(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return new ChatStreamChunk("");
        }
        String content = response.getResult().getOutput().getText();
        return new ChatStreamChunk(content == null ? "" : content);
    }
}
