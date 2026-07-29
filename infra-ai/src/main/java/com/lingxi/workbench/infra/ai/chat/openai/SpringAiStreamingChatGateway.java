package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
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
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Spring AI 的 OpenAI 兼容协议流式对话实现。
 */
@Component
@RequiredArgsConstructor
public class SpringAiStreamingChatGateway implements StreamingChatGateway {

    /**
     * 统一约束模型输出为可直接渲染的 Markdown。
     */
    private static final String SYSTEM_PROMPT = """
            你是灵析通用助手。请优先使用结构清晰、可直接渲染的 Markdown 回答。
            当用户要求展示 Markdown 表格时，不要使用代码围栏包裹表格；表头、分隔行和数据行必须连续，且每行列数保持一致。
            只有用户明确要求查看 Markdown 源码时，才使用 markdown 代码围栏。
            """;

    private final OpenAiCompatibleChatModelFactory chatModelFactory;

    /**
     * 将业务层对话命令转换为 Spring AI Prompt 并发起流式模型调用。
     *
     * <p>消息顺序固定为：系统提示词、裁剪后的历史消息、当前用户输入。
     * 返回结果被统一转换为业务无关的 {@link ChatStreamChunk}，application 不接触 Spring AI 对象。</p>
     */
    @Override
    public Flux<ChatStreamChunk> stream(ChatStreamCommand command) {
        // 每次调用根据运行配置创建 ChatModel，确保管理端修改模型或密钥后无需重启 application。
        OpenAiChatModel chatModel = chatModelFactory.create(command.modelConfig());

        // 系统提示词位于消息首位，用于约束本次模型回答的全局行为。
        List<Message> messages = new ArrayList<>();
        String systemPrompt = StringUtils.hasText(command.systemPrompt())
                ? SYSTEM_PROMPT + "\n" + command.systemPrompt()
                : SYSTEM_PROMPT;
        messages.add(new SystemMessage(systemPrompt));

        // 历史记忆已经由 application 完成轮数和 Token 裁剪，此处只负责转换为 Spring AI 消息类型。
        command.history()
                .stream()
                .map(SpringAiStreamingChatGateway::toOpenAiMessage)
                .forEach(messages::add);

        // 当前用户输入必须放在历史消息之后，作为模型本次需要回答的最后一条消息。
        messages.add(new UserMessage(command.prompt()));
        Prompt prompt = new Prompt(messages);

        // 保留有文本或真实 Usage 的分片；仅含空文本且无用量的信息不向上游传播。
        return chatModel.stream(prompt)
                .map(SpringAiStreamingChatGateway::toChunk)
                .filter(chunk -> !chunk.content().isEmpty() || chunk.hasUsage());
    }

    /** 将项目内部历史消息角色转换为 Spring AI 消息实现。 */
    private static Message toOpenAiMessage(ChatHistoryMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }

    /**
     * 从 Spring AI 响应中提取文本增量和 Token 用量。
     * 空响应被转换为空分片，由上游过滤逻辑统一丢弃。
     */
    private static ChatStreamChunk toChunk(ChatResponse response) {
        if (Objects.isNull(response)) {
            return new ChatStreamChunk("");
        }
        String content = response.getResult() == null ? "" : response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();

        Integer inputToken = null;
        Integer outputToken = null;
        Integer totalToken = null;

        if (hasActualUsage(usage)) {
            // OpenAI 兼容服务通常只在最后一个分片返回真实 Usage，普通中间分片可能返回全 0。
            inputToken = usage.getPromptTokens();
            outputToken = usage.getCompletionTokens();
            totalToken = usage.getTotalTokens();
        }
        return new ChatStreamChunk(content, inputToken, outputToken, totalToken);
    }

    /**
     * Spring AI 普通流式分片中的空 Usage 会返回全 0，需要与最终真实用量区分。
     */
    private static boolean hasActualUsage(Usage usage) {
        if (usage == null) {
            return false;
        }
        return positive(usage.getPromptTokens()) || positive(usage.getCompletionTokens()) || positive(usage.getTotalTokens());
    }

    /** 判断 Token 数是否包含实际用量。 */
    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
