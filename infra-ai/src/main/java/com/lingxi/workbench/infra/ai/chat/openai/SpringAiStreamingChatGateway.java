package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
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
import org.springframework.ai.chat.model.MessageAggregator;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Spring AI 的 OpenAI 兼容协议流式对话实现。
 */
@Component
@RequiredArgsConstructor
public class SpringAiStreamingChatGateway implements StreamingChatGateway {

    private final OpenAiCompatibleChatModelFactory chatModelFactory;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final MessageAggregator messageAggregator = new MessageAggregator();

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
                .map(runtimeConfig -> streamAnswer(chatModel, new Prompt(messages, buildToolOptions(chatModel, runtimeConfig)), runtimeConfig, 0, 0))
                .orElseGet(() -> streamAnswer(chatModel, new Prompt(messages)));
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
    private Flux<ChatStreamChunk> streamAnswer(OpenAiChatModel chatModel, Prompt prompt) {
        return streamAnswer(chatModel.stream(prompt));
    }

    /**
     * 统一转换一轮模型流，文本与 Usage 均沿用普通对话的输出契约。
     */
    private Flux<ChatStreamChunk> streamAnswer(Flux<ChatResponse> responseStream) {
        // 保留有文本或真实 Usage 的分片；仅含空文本且无用量的信息不向上游传播。
        return responseStream
                .map(SpringAiStreamingChatGateway::toChunk)
                .filter(SpringAiStreamingChatGateway::hasContentOrUsage);
    }

    /**
     * 构造本次工具对话使用的模型选项。
     */
    private OpenAiChatOptions buildToolOptions(OpenAiChatModel chatModel, AgentToolRuntimeConfig runtimeConfig) {
        int maxResultCharacters = runtimeConfig.getMaxResultCharacters();
        List<ToolCallback> callbacks = runtimeConfig.getTools()
                .stream()
                .map(tool -> boundedCallback(tool, runtimeConfig, maxResultCharacters))
                .map(ToolCallback.class::cast)
                .toList();
        return chatModel.getOptions()
                .mutate()
                .toolCallbacks(callbacks)
                // 第一阶段由模型自行判断是否需要调用工具，对应 OpenAI tool_choice=auto。
                .toolChoice("auto")
                .toolContext(Map.of("executionId", runtimeConfig.getExecutionContext().getExecutionId(), "userId", runtimeConfig.getExecutionContext().getUserId(), "conversationId", runtimeConfig.getExecutionContext().getConversationId(), "assistantMessageId", runtimeConfig.getExecutionContext().getAssistantMessageId()))
                // 第一阶段串行执行工具，保证调用上限与日志顺序确定。
                .parallelToolCalls(false)
                .build();
    }

    /**
     * 执行一轮工具感知的模型流，并在流结束后决定结束回答或继续执行工具。
     *
     * <p>本方法是工具对话和普通对话共用的回答入口。模型请求工具时先执行工具，
     * 得到新的消息历史后通过 flatMapMany 再次进入本方法；没有 tool_calls 时，
     * 当前模型流就是最终 Assistant 回答。</p>
     */
    private Flux<ChatStreamChunk> streamAnswer(OpenAiChatModel chatModel, Prompt prompt, AgentToolRuntimeConfig runtimeConfig, int completedModelCalls, int completedToolCalls) {
        return Flux.defer(() -> {
            if (completedModelCalls >= runtimeConfig.getMaxModelCalls()) {
                return Flux.error(new AgentToolException("工具调用循环超过模型调用次数限制，limit=" + runtimeConfig.getMaxModelCalls()));
            }

            AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
            Flux<ChatResponse> responseStream = messageAggregator.aggregate(chatModel.stream(prompt), aggregatedResponse::set);

            Flux<ChatStreamChunk> currentAnswer = streamAnswer(responseStream);
            return currentAnswer.concatWith(Flux.defer(() -> continueAfterModelRound(chatModel, prompt, runtimeConfig, aggregatedResponse.get(), completedModelCalls + 1, completedToolCalls)));
        });
    }

    /**
     * 模型流结束后处理完整 tool_calls；没有工具调用时，本次流已经完成最终回答。
     */
    private Flux<ChatStreamChunk> continueAfterModelRound(OpenAiChatModel chatModel, Prompt prompt, AgentToolRuntimeConfig runtimeConfig, ChatResponse response, int completedModelCalls, int completedToolCalls) {
        ChatResponse completedResponse = Objects.requireNonNull(response, "模型流结束后未生成完整响应");
        if (!completedResponse.hasToolCalls()) {
            return Flux.empty();
        }

        int requestedToolCalls = Objects.requireNonNull(completedResponse.getResult()).getOutput().getToolCalls().size();
        int totalToolCalls = completedToolCalls + requestedToolCalls;
        if (totalToolCalls > runtimeConfig.getMaxToolCalls()) {
            return Flux.error(new AgentToolException("模型请求的工具调用次数超过Agent限制，limit=" + runtimeConfig.getMaxToolCalls()));
        }

        return executeToolCalling(prompt, completedResponse)
                .flatMapMany(result -> {
                    Prompt answerPrompt = new Prompt(result.conversationHistory(), prompt.getOptions());
                    // 重新递归
                    return streamAnswer(chatModel, answerPrompt, runtimeConfig, completedModelCalls, totalToolCalls);
                });
    }

    /**
     * 执行模型请求的工具，返回包含 Assistant tool_calls 和 ToolResponseMessage 的消息历史。
     *
     * <p>MCP 和本地工具当前均为同步调用，统一放入 boundedElastic，避免阻塞模型流订阅线程。</p>
     */
    private Mono<ToolExecutionResult> executeToolCalling(Prompt prompt, ChatResponse response) {
        return Mono.fromCallable(() -> toolCallingManager.executeToolCalls(prompt, response))
                .subscribeOn(Schedulers.boundedElastic());
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
