package com.lingxi.workbench.infra.ai.chat.openai;

import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
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
import reactor.util.context.ContextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Spring AI 的 OpenAI 兼容协议流式对话实现。
 *
 * <p>将业务层 {@link ChatStreamCommand} 转换为 Spring AI Prompt，调用 OpenAI 兼容模型流式生成，
 * 并把响应分片统一转换为业务无关的 {@link ChatStreamChunk} 交给上层。</p>
 *
 * <p>工具对话支持完整的工具调用循环：模型请求工具时先执行工具、把结果回填进消息历史后再次请求模型，
 * 直至模型不再请求工具或命中预算/额度边界；循环期间通过 MessageAggregator 聚合流式分片以解析 tool_calls，
 * 并在整轮结束时向调用方汇总一次 Token 用量快照。</p>
 */
@Component
@RequiredArgsConstructor
public class SpringAiStreamingChatGateway implements StreamingChatGateway {

    private final OpenAiCompatibleChatModelFactory chatModelFactory;
    private final ObservationRegistry observationRegistry;
    private final ToolCallingManager toolCallingManager;
    private final MessageAggregator messageAggregator = new MessageAggregator();


    /**
     * 将业务层对话命令转换为 Spring AI Prompt 并发起流式模型调用。
     *
     * <p>消息顺序固定为：系统提示词、裁剪后的历史消息、当前用户输入。
     * 返回结果被统一转换为业务无关的 {@link ChatStreamChunk}，application 不接触 Spring AI 对象。</p>
     */
    @Override
    public Flux<ChatStreamChunk> stream(ChatStreamCommand command) {
        Observation parent = observationRegistry.getCurrentObservation();
        // 每次调用根据运行配置创建 ChatModel，确保管理端修改模型或密钥后无需重启 application。
        OpenAiChatModel chatModel = chatModelFactory.create(command.modelConfig());
        List<Message> messages = buildMessages(command);

        Flux<ChatStreamChunk> stream = command.toolRuntimeConfig()
                .filter(AgentToolRuntimeConfig::enabled)
                .map(runtimeConfig -> streamToolAnswer(chatModel, messages, runtimeConfig))
                .orElseGet(() -> streamAnswer(chatModel, new Prompt(messages)));
        if (Objects.isNull(parent)) {
            return stream;
        }
        // Spring AI 流式观测从 Reactor Context 读取父 Observation，不能依赖发起线程的 ThreadLocal。
        return stream.contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, parent));
    }

    /**
     * 为一次工具增强回答创建独立循环状态，禁止跨请求共享执行记忆。
     */
    private Flux<ChatStreamChunk> streamToolAnswer(OpenAiChatModel chatModel, List<Message> messages, AgentToolRuntimeConfig runtimeConfig) {
        AgentLoopState loopState = new AgentLoopState(runtimeConfig);
        Prompt prompt = new Prompt(messages, buildToolOptions(chatModel, runtimeConfig, loopState));
        return streamAnswer(chatModel, prompt, runtimeConfig, loopState);
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
        return Flux.defer(() -> {
            AtomicReference<TokenUsage> latestUsage = new AtomicReference<>(TokenUsage.empty());
            Flux<ChatStreamChunk> contentChunks = responseStream
                    .map(response -> toContentChunk(response, latestUsage))
                    // 分片过滤：部分供应商会推送仅携带 Usage 的空文本分片，丢弃它们避免污染下游输出流。
                    .filter(chunk -> !chunk.content().isEmpty());

            // Spring AI Usage 表示当前模型请求的累计快照，不是当前文本分片的增量。
            // 整轮结束后只向上游发送一次最终快照，避免兼容供应商逐片重复返回 Usage 时被重复累加。
            return contentChunks.concatWith(Mono.defer(() -> finalUsageChunk(latestUsage.get())));
        });
    }

    /**
     * 构造本次工具对话使用的模型选项。
     */
    private OpenAiChatOptions buildToolOptions(OpenAiChatModel chatModel, AgentToolRuntimeConfig runtimeConfig, AgentLoopState loopState) {
        int maxResultCharacters = runtimeConfig.getMaxResultCharacters();
        // 用带结果长度上限的包装回调替换原始工具，防止单个工具返回超长结果挤占上下文窗口。
        List<ToolCallback> callbacks = runtimeConfig.getTools()
                .stream()
                .map(tool -> boundedCallback(tool, runtimeConfig, maxResultCharacters, loopState))
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
    private Flux<ChatStreamChunk> streamAnswer(OpenAiChatModel chatModel, Prompt prompt, AgentToolRuntimeConfig runtimeConfig, AgentLoopState loopState) {
        return Flux.defer(() -> {
            loopState.beginModelCall();

            AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
            // 消息聚合：MessageAggregator 透传原始分片的同时，把整轮流聚合成完整 ChatResponse 存入引用，
            // 供流结束后解析 tool_calls 使用，不影响文本分片实时下发。
            Flux<ChatResponse> responseStream = messageAggregator.aggregate(chatModel.stream(prompt), aggregatedResponse::set);

            Flux<ChatStreamChunk> currentAnswer = streamAnswer(responseStream);
            return currentAnswer.concatWith(Flux.defer(() -> continueAfterModelRound(chatModel, prompt, runtimeConfig, loopState, aggregatedResponse.get())));
        });
    }

    /**
     * 模型流结束后处理完整 tool_calls；没有工具调用时，本次流已经完成最终回答。
     */
    private Flux<ChatStreamChunk> continueAfterModelRound(OpenAiChatModel chatModel, Prompt prompt, AgentToolRuntimeConfig runtimeConfig, AgentLoopState loopState,
                                                          ChatResponse response) {
        ChatResponse completedResponse = Objects.requireNonNull(response, "模型流结束后未生成完整响应");
        loopState.recordUsage(TokenUsageConverter.convertActual(completedResponse.getMetadata().getUsage()));
        if (!completedResponse.hasToolCalls()) {
            return Flux.empty();
        }

        // 工具循环预算控制：执行工具前先完成本轮调用记账，预算或额度边界命中时立即收敛为最终回答，
        // 避免为注定终止的循环再消耗一次模型调用。
        int requestedToolCalls = Objects.requireNonNull(completedResponse.getResult()).getOutput().getToolCalls().size();
        if (loopState.shouldFinalizeBeforeToolExecution()) {
            return streamFinalAnswer(chatModel, prompt, loopState, "模型工作预算已到边界");
        }
        if (!loopState.tryRecordToolCalls(requestedToolCalls)) {
            return streamFinalAnswer(chatModel, prompt, loopState, "工具调用额度已用尽");
        }

        // 工具循环主体：执行模型请求的工具，把“Assistant tool_calls + 工具结果”回填进消息历史后，
        // 携带新历史再次请求模型，形成“模型→工具→模型”迭代，直到模型不再请求工具。
        return executeToolCalling(prompt, completedResponse)
                .flatMapMany(result -> {
                    Prompt answerPrompt = new Prompt(result.conversationHistory(), prompt.getOptions());
                    if (loopState.shouldFinalize()) {
                        return streamFinalAnswer(chatModel, answerPrompt, loopState, "运行预算已到边界或工具结果已经重复");
                    }
                    return streamAnswer(chatModel, answerPrompt, runtimeConfig, loopState);
                });
    }

    /**
     * 预算接近上限或命中重复调用时，保留一次禁用工具的模型调用用于整理已有证据。
     */
    private Flux<ChatStreamChunk> streamFinalAnswer(OpenAiChatModel chatModel, Prompt prompt, AgentLoopState loopState,
                                                    String reason) {
        return Flux.defer(() -> {
            loopState.beginFinalModelCall();
            List<Message> messages = new ArrayList<>(prompt.getInstructions());
            // 终答阶段：把终止原因写入系统提示词并清空全部工具（tool_choice=none），
            // 强制模型基于已有证据收敛答案，防止再次进入工具循环。
            messages.add(new SystemMessage(reason + "。禁止继续调用工具、委派或规划；请优先基于已有证据给出可用答案。证据不足时只提出一个最关键的补充问题，并明确说明当前结论的限制。"));
            OpenAiChatOptions options = ((OpenAiChatOptions) prompt.getOptions()).mutate()
                    .toolCallbacks(List.of())
                    .toolChoice("none")
                    .build();
            return streamAnswer(chatModel, new Prompt(messages, options));
        });
    }

    /**
     * 执行模型请求的工具，返回包含 Assistant tool_calls 和 ToolResponseMessage 的消息历史。
     *
     * <p>MCP 和本地工具当前均为同步调用，统一放入 boundedElastic，避免阻塞模型流订阅线程。</p>
     */
    private Mono<ToolExecutionResult> executeToolCalling(Prompt prompt, ChatResponse response) {
        return Mono.deferContextual(context -> Mono
                .fromCallable(() -> executeToolCallingWithParent(prompt, response, observationFrom(context)))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 工具执行切换到 boundedElastic 后恢复父 Observation，确保 Tool span 与模型链路相关联。
     */
    private ToolExecutionResult executeToolCallingWithParent(Prompt prompt, ChatResponse response, Observation parent) {
        if (Objects.isNull(parent)) {
            return toolCallingManager.executeToolCalls(prompt, response);
        }
        try (Observation.Scope ignored = parent.openScope()) {
            return toolCallingManager.executeToolCalls(prompt, response);
        }
    }

    /**
     * 从 Reactor Context 中读取父 Observation；未写入观测上下文时返回 null。
     */
    private static Observation observationFrom(ContextView context) {
        return context.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
    }

    /**
     * 从完整模型响应中提取最终文本；无结果对象时返回空串。
     */
    private static String finalContent(ChatResponse response) {
        return Optional.ofNullable(response.getResult())
                .map(result -> result.getOutput().getText())
                .orElse("");
    }

    /**
     * 从 Spring AI 响应中提取文本，并保留本轮最后一个真实 Usage 快照。
     */
    private static ChatStreamChunk toContentChunk(ChatResponse response, AtomicReference<TokenUsage> latestUsage) {
        if (Objects.isNull(response)) {
            return new ChatStreamChunk("");
        }
        String content = finalContent(response);
        TokenUsage usage = TokenUsageConverter.convertActual(response.getMetadata().getUsage());
        if (usage.hasUsage()) {
            latestUsage.set(usage);
        }
        return new ChatStreamChunk(content);
    }

    /**
     * 模型整轮结束时只发布一次最终 Usage；供应商未返回时不制造空分片。
     */
    private static Mono<ChatStreamChunk> finalUsageChunk(TokenUsage usage) {
        if (!usage.hasUsage()) {
            return Mono.empty();
        }
        return Mono.just(new ChatStreamChunk("", usage));
    }

    /**
     * 将业务工具包装为带结果长度上限的 Spring AI 工具回调。
     *
     * <p>执行上下文、事件发布器和循环状态一并注入包装回调，使工具执行可观测且结果大小可控。</p>
     */
    private static BoundedToolCallback boundedCallback(AgentTool tool, AgentToolRuntimeConfig runtimeConfig, int maxResultCharacters,
                                                       AgentLoopState loopState) {
        return new BoundedToolCallback(tool, runtimeConfig.getExecutionContext(), maxResultCharacters, runtimeConfig.resolveEventPublisher(), loopState);
    }

}
