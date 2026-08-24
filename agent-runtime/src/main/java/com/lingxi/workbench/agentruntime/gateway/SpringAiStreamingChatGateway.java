package com.lingxi.workbench.agentruntime.gateway;

import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.checkpoint.SpringAiCheckpointCodec;
import com.lingxi.workbench.agentruntime.checkpoint.ToolApprovalFingerprint;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeContextAdvisor;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeModelAdvisor;
import com.lingxi.workbench.agentruntime.hook.HumanApprovalHook;
import com.lingxi.workbench.agentruntime.loop.RuntimeToolCallback;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
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
public class SpringAiStreamingChatGateway implements StreamingChatGateway {

    private final OpenAiCompatibleChatModelFactory chatModelFactory;
    private final ObservationRegistry observationRegistry;
    private final ToolCallingManager toolCallingManager;
    private final SpringAiChatClientFactory chatClientFactory;
    private final RuntimeContextAdvisor runtimeContextAdvisor;
    private final RuntimeModelAdvisor runtimeModelAdvisor;
    private final HumanApprovalHook humanApprovalHook;
    private final SpringAiCheckpointCodec checkpointCodec;
    private final MessageAggregator messageAggregator = new MessageAggregator();

    @Autowired
    public SpringAiStreamingChatGateway(OpenAiCompatibleChatModelFactory chatModelFactory,
                                        ObservationRegistry observationRegistry,
                                        ToolCallingManager toolCallingManager,
                                        SpringAiChatClientFactory chatClientFactory,
                                        RuntimeContextAdvisor runtimeContextAdvisor,
                                        RuntimeModelAdvisor runtimeModelAdvisor,
                                        HumanApprovalHook humanApprovalHook,
                                        SpringAiCheckpointCodec checkpointCodec) {
        this.chatModelFactory = chatModelFactory;
        this.observationRegistry = observationRegistry;
        this.toolCallingManager = toolCallingManager;
        this.chatClientFactory = chatClientFactory;
        this.runtimeContextAdvisor = runtimeContextAdvisor;
        this.runtimeModelAdvisor = runtimeModelAdvisor;
        this.humanApprovalHook = humanApprovalHook;
        this.checkpointCodec = checkpointCodec;
    }

    /**
     * 单元测试使用的等价装配，仍让流式调用经过真实 ChatClient。
     */
    SpringAiStreamingChatGateway(OpenAiCompatibleChatModelFactory chatModelFactory,
                                 ObservationRegistry observationRegistry,
                                 ToolCallingManager toolCallingManager) {
        this(chatModelFactory, observationRegistry, toolCallingManager,
                new SpringAiChatClientFactory(observationRegistry),
                new RuntimeContextAdvisor(), new RuntimeModelAdvisor(),
                new HumanApprovalHook(new SpringAiCheckpointCodec(), new ToolApprovalFingerprint()),
                new SpringAiCheckpointCodec());
    }

    /**
     * 将业务层对话命令转换为 Spring AI Prompt 并发起流式模型调用。
     *
     * <p>消息顺序固定为：系统提示词、裁剪后的历史消息、当前用户输入。
     * 返回结果被统一转换为业务无关的 {@link ChatStreamChunk}，application 不接触 Spring AI 对象。</p>
     */
    @Override
    public Flux<ChatStreamChunk> stream(ChatStreamCommand command) {
        return stream(command, null);
    }

    /**
     * AgentRuntime 调用携带唯一 ExecutionState，以 Advisor 执行预算和事件记账。
     */
    public Flux<ChatStreamChunk> stream(ChatStreamCommand command, ExecutionState executionState) {
        Observation parent = observationRegistry.getCurrentObservation();
        // 每次调用根据运行配置创建 ChatModel，确保管理端修改模型或密钥后无需重启 application。
        OpenAiChatModel chatModel = chatModelFactory.create(command.modelConfig());
        List<Message> messages = buildMessages(command);

        Flux<ChatStreamChunk> stream = command.toolRuntimeConfig()
                .filter(AgentToolRuntimeConfig::enabled)
                .map(runtimeConfig -> streamToolAnswer(chatModel, messages,
                        Objects.requireNonNull(executionState, "Tool调用必须由AgentRuntime提供ExecutionState")))
                .orElseGet(() -> streamAnswer(chatModel, new Prompt(messages), executionState));
        if (Objects.isNull(parent)) {
            return stream;
        }
        // Spring AI 流式观测从 Reactor Context 读取父 Observation，不能依赖发起线程的 ThreadLocal。
        return stream.contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, parent));
    }

    /**
     * 从持久消息历史恢复，并先执行已批准的原始ToolCall。
     */
    public Flux<ChatStreamChunk> resume(ChatStreamCommand command, ExecutionState state,
                                        RuntimeCheckpoint checkpoint, Long interactionId,
                                        String approvalFingerprint) {
        Observation parent = observationRegistry.getCurrentObservation();
        OpenAiChatModel chatModel = chatModelFactory.create(command.modelConfig());
        state.ensureResumable();
        humanApprovalHook.validateResume(checkpoint, state);
        Prompt prompt = new Prompt(checkpointCodec.decodeMessages(checkpoint.getMessages()),
                buildToolOptions(chatModel, state));
        Long invocationId = state.checkpointStore().claimApproved(
                checkpoint, interactionId, approvalFingerprint);
        state.prepareApprovedInvocation(invocationId, checkpoint.getPendingToolCall());
        state.humanResolved(RuntimeInterruption.builder()
                .interactionId(interactionId)
                .fingerprint(approvalFingerprint)
                .expiresAt(checkpoint.getExpiresAt())
                .build());
        ChatResponse pendingResponse = checkpointCodec.pendingToolResponse(checkpoint.getPendingToolCall());
        Flux<ChatStreamChunk> stream = executeToolCalling(prompt, pendingResponse)
                .flatMapMany(result -> {
                    Prompt answerPrompt = new Prompt(result.conversationHistory(), prompt.getOptions());
                    if (state.shouldFinalize()) {
                        return streamFinalAnswer(chatModel, answerPrompt, state, state.finalizationReason());
                    }
                    return streamToolRound(chatModel, answerPrompt, state);
                });
        if (parent == null) {
            return stream;
        }
        return stream.contextWrite(context -> context.put(ObservationThreadLocalAccessor.KEY, parent));
    }

    /**
     * 使用 Runtime 唯一 ExecutionState 开始工具增强回答。
     */
    private Flux<ChatStreamChunk> streamToolAnswer(OpenAiChatModel chatModel, List<Message> messages,
                                                   ExecutionState state) {
        Prompt prompt = new Prompt(messages, buildToolOptions(chatModel, state));
        return streamToolRound(chatModel, prompt, state);
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
        return streamAnswer(chatModel, prompt, null);
    }

    /**
     * 普通流式回答统一从 ChatClient 进入，并按需挂载 Runtime Advisor。
     */
    private Flux<ChatStreamChunk> streamAnswer(OpenAiChatModel chatModel, Prompt prompt,
                                               ExecutionState executionState) {
        boolean finalModelCall = executionState != null && executionState.isFinalizationRequested();
        return streamAnswer(streamResponses(chatModel, prompt, executionState, finalModelCall));
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
    private OpenAiChatOptions buildToolOptions(OpenAiChatModel chatModel, ExecutionState state) {
        AgentToolRuntimeConfig runtimeConfig = state.toolRuntimeConfig();
        int maxResultCharacters = runtimeConfig.getMaxResultCharacters();
        // 用带结果长度上限的包装回调替换原始工具，防止单个工具返回超长结果挤占上下文窗口。
        List<ToolCallback> callbacks = runtimeConfig.getTools()
                .stream()
                .map(tool -> runtimeCallback(tool, runtimeConfig, maxResultCharacters, state))
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
    private Flux<ChatStreamChunk> streamToolRound(OpenAiChatModel chatModel, Prompt prompt,
                                                  ExecutionState state) {
        return Flux.defer(() -> {
            AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
            // Tool轮次必须先聚合完整响应再判断是否含tool_calls，禁止把中间推理文本混入最终回答。
            Flux<ChatResponse> responseStream = messageAggregator.aggregate(
                    streamResponses(chatModel, prompt, state, false), aggregatedResponse::set);
            return responseStream.thenMany(Flux.defer(() ->
                    continueAfterModelRound(chatModel, prompt, state, aggregatedResponse.get())));
        });
    }

    /**
     * 非流式和流式模型调用的统一 Spring AI 组合根。
     * ToolCallingAdvisor 保持关闭，Tool 循环继续由 Lingxi 以 user-controlled 模式管理。
     */
    private Flux<ChatResponse> streamResponses(OpenAiChatModel chatModel, Prompt prompt,
                                               ExecutionState executionState) {
        return streamResponses(chatModel, prompt, executionState, false);
    }

    private Flux<ChatResponse> streamResponses(OpenAiChatModel chatModel, Prompt prompt,
                                               ExecutionState executionState, boolean finalModelCall) {
        ChatClient.ChatClientRequestSpec request = chatClientFactory.create(chatModel)
                .prompt(prompt)
                .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false));
        if (executionState != null) {
            request = request.advisors(spec -> spec
                    .advisors(runtimeContextAdvisor, runtimeModelAdvisor)
                    .param(RuntimeContextAdvisor.EXECUTION_STATE, executionState)
                    .param(RuntimeModelAdvisor.FINAL_MODEL_CALL, finalModelCall));
        }
        return request.stream().chatResponse();
    }

    /**
     * 模型流结束后处理完整 tool_calls；没有工具调用时，本次流已经完成最终回答。
     */
    private Flux<ChatStreamChunk> continueAfterModelRound(OpenAiChatModel chatModel, Prompt prompt,
                                                          ExecutionState state, ChatResponse response) {
        ChatResponse completedResponse = Objects.requireNonNull(response, "模型流结束后未生成完整响应");
        if (!completedResponse.hasToolCalls()) {
            return completedAnswer(completedResponse);
        }

        // 工具循环预算控制：执行工具前先完成本轮调用记账，预算或额度边界命中时立即收敛为最终回答，
        // 避免为注定终止的循环再消耗一次模型调用。
        if (state.shouldFinalizeBeforeToolExecution()) {
            return streamFinalAnswer(chatModel, prompt, state, "模型工作预算已到边界");
        }
        List<AssistantMessage.ToolCall> requestedCalls = Objects.requireNonNull(
                completedResponse.getResult()).getOutput().getToolCalls();
        ExecutionState.ToolCallSelection selection = state.selectToolCalls(requestedCalls);
        if (selection.approvedCalls().isEmpty()) {
            return streamFinalAnswer(chatModel, prompt, state, selection.reason());
        }
        ChatResponse approvedResponse = restrictToolCalls(completedResponse, selection.approvedCalls());

        return Mono.fromCallable(() -> humanApprovalHook.beforeTool(prompt, approvedResponse, state))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(interruption -> {
                    if (interruption.isPresent()) {
                        return Flux.empty();
                    }
                    // Tool循环主体由Spring AI执行原始ToolCall并回填标准消息历史。
                    return executeToolCalling(prompt, approvedResponse)
                            .flatMapMany(result -> {
                                Prompt answerPrompt = new Prompt(result.conversationHistory(), prompt.getOptions());
                                if (state.shouldFinalize()) {
                                    return streamFinalAnswer(chatModel, answerPrompt, state,
                                            state.finalizationReason());
                                }
                                return streamToolRound(chatModel, answerPrompt, state);
                            });
                });
    }

    /**
     * 预算接近上限或命中重复调用时，保留一次禁用工具的模型调用用于整理已有证据。
     */
    private Flux<ChatStreamChunk> streamFinalAnswer(OpenAiChatModel chatModel, Prompt prompt,
                                                    ExecutionState state, String reason) {
        return Flux.defer(() -> {
            state.requestFinalization(reason);
            List<Message> messages = new ArrayList<>(prompt.getInstructions());
            // 终答阶段：把终止原因写入系统提示词并清空全部工具（tool_choice=none），
            // 强制模型基于已有证据收敛答案，防止再次进入工具循环。
            messages.add(new SystemMessage(reason + "。禁止继续调用工具、委派或规划；请优先基于已有证据给出可用答案。证据不足时只提出一个最关键的补充问题，并明确说明当前结论的限制。"));
            OpenAiChatOptions options = ((OpenAiChatOptions) prompt.getOptions()).mutate()
                    .toolCallbacks(List.of())
                    .toolChoice("none")
                    .build();
            return streamAnswer(streamResponses(chatModel, new Prompt(messages, options), state, true));
        });
    }

    /**
     * 只保留平台批准的工具调用，防止供应商忽略串行选项后批量执行过多工具。
     */
    private static ChatResponse restrictToolCalls(ChatResponse response, List<AssistantMessage.ToolCall> approvedCalls) {
        Generation original = Objects.requireNonNull(response.getResult(), "工具响应结果不能为空");
        AssistantMessage output = original.getOutput();
        AssistantMessage approvedOutput = AssistantMessage.builder()
                .content(output.getText())
                .properties(output.getMetadata())
                .toolCalls(approvedCalls)
                .media(output.getMedia())
                .build();
        Generation approvedGeneration = new Generation(approvedOutput, original.getMetadata());
        return new ChatResponse(List.of(approvedGeneration), response.getMetadata());
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
     * <p>可信执行上下文与唯一 ExecutionState 一并注入包装回调，使工具执行可观测且结果大小可控。</p>
     */
    private static RuntimeToolCallback runtimeCallback(AgentTool tool, AgentToolRuntimeConfig runtimeConfig,
                                                       int maxResultCharacters, ExecutionState state) {
        return new RuntimeToolCallback(tool, runtimeConfig.getExecutionContext(), maxResultCharacters,
                state);
    }

    /**
     * Tool轮次确认不含Tool调用后，才把完整最终文本与本轮Usage交给消息输出通道。
     */
    private static Flux<ChatStreamChunk> completedAnswer(ChatResponse response) {
        String content = finalContent(response);
        TokenUsage usage = TokenUsageConverter.convertActual(response.getMetadata().getUsage());
        if (content.isEmpty() && !usage.hasUsage()) {
            return Flux.empty();
        }
        return Flux.just(new ChatStreamChunk(content, usage));
    }

}
