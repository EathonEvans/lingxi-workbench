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

/**
 * 基于 Spring AI OpenAI 兼容协议的 DeepSeek 流式对话实现。
 *
 * <p>DeepSeek 官方接口兼容 OpenAI Chat Completions 协议。这里使用
 * {@link OpenAiChatModel} 保证流式片段按服务端到达顺序传递，同时保留
 * {@link StreamingChatGateway} 作为项目内部稳定的 AI 调用边界。</p>
 */
@Component
@RequiredArgsConstructor
public class SpringAiDeepSeekStreamingChatGateway implements StreamingChatGateway {

    /**
     * 统一约束模型输出为可直接渲染的 Markdown，避免展示型内容被代码围栏包裹。
     */
    private static final String SYSTEM_PROMPT = """
            你是灵析通用助手。请优先使用结构清晰、可直接渲染的 Markdown 回答。
            当用户要求展示 Markdown 表格时，不要使用代码围栏包裹表格；表头、分隔行和数据行必须连续，且每行列数保持一致。
            只有用户明确要求查看 Markdown 源码时，才使用 markdown 代码围栏。
            """;

    private final OpenAiChatModel chatModel;

    @Override
    public Flux<ChatStreamChunk> stream(ChatStreamCommand command) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(command.prompt())
        ));
        return chatModel.stream(prompt)
                .map(SpringAiDeepSeekStreamingChatGateway::toChunk)
                .filter(chunk -> !chunk.content().isEmpty());
    }

    private static ChatStreamChunk toChunk(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return new ChatStreamChunk("");
        }
        String content = response.getResult().getOutput().getText();
        return new ChatStreamChunk(content == null ? "" : content);
    }
}
