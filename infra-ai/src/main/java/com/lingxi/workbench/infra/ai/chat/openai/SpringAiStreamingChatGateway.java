package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Spring AI 的 OpenAI 兼容协议流式对话实现。
 */
@Component
@RequiredArgsConstructor
public class SpringAiStreamingChatGateway implements StreamingChatGateway {

    private final OpenAiCompatibleChatModelFactory chatModelFactory;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

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
        List<Message> messages = buildMessages(command);

        return command.toolRuntimeConfig()
                .filter(AgentToolRuntimeConfig::enabled)
                .map(runtimeConfig -> generateToolAssistedAnswer(chatModel, messages, runtimeConfig))
                .orElseGet(() -> streamDirectAnswer(chatModel, messages));
    }

    /**
     * 按固定顺序构建本次模型调用的消息列表。
     */
    private static List<Message> buildMessages(ChatStreamCommand command) {
        List<Message> messages = new ArrayList<>();
        // 系统提示词位于消息首位，用于约束本次模型回答的全局行为。
        messages.add(new SystemMessage(command.systemPrompt()));

        // 历史记忆已经由 application 完成轮数和 Token 裁剪，此处只负责转换为 Spring AI 消息类型。
        command.history().stream().map(ChatHistoryMessage::format).forEach(messages::add);

        // 当前用户输入必须放在历史消息之后，作为模型本次需要回答的最后一条消息。
        messages.add(new UserMessage(command.prompt()));
        return messages;
    }

    /**
     * 普通对话直接透传模型的流式文本分片。
     */
    private Flux<ChatStreamChunk> streamDirectAnswer(OpenAiChatModel chatModel, List<Message> messages) {
        // 保留有文本或真实 Usage 的分片；仅含空文本且无用量的信息不向上游传播。
        return chatModel.stream(new Prompt(messages))
                .map(SpringAiStreamingChatGateway::toChunk)
                .filter(SpringAiStreamingChatGateway::hasContentOrUsage);
    }

    /**
     * 在阻塞线程池中生成工具辅助回答，并适配为统一的 Flux 输出契约。
     *
     * <p>第一阶段的 Function Calling 需要在每轮拿到完整 tool_calls 后才能执行工具，
     * 因此内部是同步循环，最终只产生一个完整回答。这里明确使用 Mono 表达单结果，
     * 再转换为 Flux；{@code boundedElastic} 防止同步模型和工具调用阻塞 Reactor
     * 订阅线程。</p>
     */
    private Flux<ChatStreamChunk> generateToolAssistedAnswer(OpenAiChatModel chatModel, List<Message> messages, AgentToolRuntimeConfig runtimeConfig) {
        return Mono.fromCallable(() -> runToolCallingLoop(chatModel, messages, runtimeConfig))
                .subscribeOn(Schedulers.boundedElastic())
                .flux();
    }

    /**
     * 执行受控 Function Calling 循环。
     *
     * <p>工具调用需要读取完整 Assistant tool_calls 后才能执行，因此工具模式下各轮使用
     * 非流式模型调用；最终答案仍通过统一的 ChatStreamChunk 返回给上层。模型调用次数和
     * 工具调用次数分别设置硬上限，避免模型在工具之间无限循环。</p>
     */
    private ChatStreamChunk runToolCallingLoop(OpenAiChatModel chatModel, List<Message> messages, AgentToolRuntimeConfig runtimeConfig) {
        int maxToolCalls = runtimeConfig.getMaxToolCalls();
        int maxModelCalls = runtimeConfig.getMaxModelCalls();
        int maxResultCharacters = runtimeConfig.getMaxResultCharacters();

        List<ToolCallback> callbacks = runtimeConfig.getTools()
                .stream()
                .map(tool -> boundedCallback(tool, runtimeConfig, maxResultCharacters))
                .map(ToolCallback.class::cast)
                .toList();
        OpenAiChatOptions options = chatModel.getOptions()
                .mutate()
                .toolCallbacks(callbacks)
                // 第一阶段由模型自行判断是否需要调用工具，对应 OpenAI tool_choice=auto。
                .toolChoice("auto")
                .toolContext(Map.of("executionId", runtimeConfig.getExecutionContext().getExecutionId(), "userId", runtimeConfig.getExecutionContext().getUserId(), "conversationId", runtimeConfig.getExecutionContext().getConversationId(), "assistantMessageId", runtimeConfig.getExecutionContext().getAssistantMessageId()))
                // 第一阶段串行执行工具，保证调用上限与日志顺序确定。
                .parallelToolCalls(false)
                .build();

        Prompt currentPrompt = new Prompt(messages, options);
        int modelCalls = 0;
        int toolCalls = 0;
        TokenUsageAccumulator usageAccumulator = new TokenUsageAccumulator();

        while (modelCalls < maxModelCalls) {
            ChatResponse response = chatModel.call(currentPrompt);
            modelCalls++;
            usageAccumulator.add(TokenUsageConverter.convert(response.getMetadata().getUsage()));

            if (!response.hasToolCalls()) {
                return new ChatStreamChunk(finalContent(response), usageAccumulator.snapshot());
            }

            int requestedToolCalls = Objects.requireNonNull(response.getResult())
                    .getOutput()
                    .getToolCalls().size();
            if (toolCalls + requestedToolCalls > maxToolCalls) {
                throw new AgentToolException("模型请求的工具调用次数超过Agent限制，limit=" + maxToolCalls);
            }
            toolCalls += requestedToolCalls;
            ToolExecutionResult executionResult = toolCallingManager.executeToolCalls(currentPrompt, response);
            currentPrompt = new Prompt(executionResult.conversationHistory(), options);
        }
        throw new AgentToolException("工具调用循环超过模型调用次数限制，limit=" + maxModelCalls);
    }

    private static String finalContent(ChatResponse response) {
        return Optional.ofNullable(response.getResult())
                .map(result -> result.getOutput().getText())
                .orElse("");
    }

    /**
     * 从 Spring AI 响应中提取文本增量和 Token 用量。
     * 空响应被转换为空分片，由上游过滤逻辑统一丢弃。
     */
    private static ChatStreamChunk toChunk(ChatResponse response) {
        if (Objects.isNull(response)) {
            return new ChatStreamChunk("");
        }
        String content = finalContent(response);
        TokenUsage usage = TokenUsageConverter.convertActual(response.getMetadata().getUsage());
        return new ChatStreamChunk(content, usage);
    }

    /**
     * 过滤供应商返回的无文本、无 Token 用量空分片。
     */
    private static boolean hasContentOrUsage(ChatStreamChunk chunk) {
        return !chunk.content().isEmpty() || chunk.hasUsage();
    }

    private static BoundedToolCallback boundedCallback(AgentTool tool, AgentToolRuntimeConfig runtimeConfig, int maxResultCharacters) {
        return new BoundedToolCallback(tool, runtimeConfig.getExecutionContext(), maxResultCharacters, runtimeConfig.resolveEventPublisher());
    }

}
