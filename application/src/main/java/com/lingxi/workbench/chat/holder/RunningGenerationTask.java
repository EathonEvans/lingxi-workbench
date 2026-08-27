package com.lingxi.workbench.chat.holder;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatInteractionEventBO;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ChatStepOutputEventBO;
import com.lingxi.workbench.chat.repo.ChatGenerationEventRepo;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.infra.ai.agent.model.AgentProgressEvent;
import com.lingxi.workbench.observability.service.TraceRun;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.Builder;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;

/**
 * 单个模型生成任务及其本机可重放事件。
 *
 * <p>该对象只存在于当前服务进程，文本与工具增量同时写入 Redis 短期缓存，
 * 最终回答仍由对话服务持久化到 PostgreSQL。所有可能由不同线程调用的状态方法均进行同步，
 * 防止模型回调、用户取消和应用关闭同时修改回答内容或重复进入终态。</p>
 */
public final class RunningGenerationTask implements ChatExecutionEventPublisher {

    /**
     * 对应数据库中的 Assistant 消息主键。
     */
    private final ChatTurnBO turn;
    /**
     * 所属用户主键。
     */
    private final Long userId;
    /**
     * 本轮冻结的 ApplicationVersion 主键。
     */
    private final Long applicationVersionId;
    /**
     * Application 级记忆系统配置。
     */
    private final MemoryConfig memoryConfig;
    /**
     * Redis 增量事件仓库，使浏览器刷新和本机任务释放后仍可恢复工具状态与文本。
     */
    private final ChatGenerationEventRepo eventRepo;
    /**
     * 后台生成任务拥有的根 Trace 生命周期。
     */
    private final TraceRun traceRun;
    /**
     * 按到达顺序累积模型文本增量，终态时一次性保存完整或部分回答。
     */
    private final StringBuilder answer = new StringBuilder();
    /**
     * 进程内事件总线。replay().all() 允许较晚建立的 SSE 订阅者重放本进程已经产生的事件。
     */
    private final Sinks.Many<ChatGenerationEventBO> eventSink = Sinks.many().replay().all();

    /**
     * 文本与工具状态事件共享的单调递增序号，用于客户端断线后按序号续传。
     */
    private long sequence;
    /**
     * 是否已经进入完成、失败、取消或中断中的任一终态。
     */
    private boolean terminal;
    /**
     * 服务端持有的模型 Flux 订阅，用户取消或应用关闭时通过它停止上游调用。
     */
    private Disposable modelSubscription;
    /**
     * 最近一个携带真实 Usage 的模型分片返回的 Token 用量。
     */
    private TokenUsage tokenUsage = TokenUsage.empty();

    @Builder
    private RunningGenerationTask(ChatTurnBO turn, Long userId, Long applicationVersionId, MemoryConfig memoryConfig,
                                  ChatGenerationEventRepo eventRepo, TraceRun traceRun, String initialAnswer,
                                  TokenUsage initialUsage, Long initialSequence) {
        this.turn = Objects.requireNonNull(turn, "对话轮次不能为空");
        this.userId = userId;
        this.applicationVersionId = Objects.requireNonNull(applicationVersionId, "ApplicationVersion不能为空");
        this.memoryConfig = Objects.requireNonNullElseGet(memoryConfig, MemoryConfig::disabled);
        this.eventRepo = eventRepo;
        this.traceRun = Objects.requireNonNull(traceRun, "Agent根Trace不能为空");
        if (CharSequenceUtil.isNotEmpty(initialAnswer)) {
            answer.append(initialAnswer);
        }
        tokenUsage = Objects.requireNonNullElseGet(initialUsage, TokenUsage::empty);
        sequence = Objects.requireNonNullElse(initialSequence, 0L);
    }

    public Long id() {
        return turn.assistantMessageId();
    }

    public Long userId() {
        return userId;
    }

    public Long applicationVersionId() {
        return applicationVersionId;
    }

    public ChatTurnBO turn() {
        return turn;
    }

    public MemoryConfig memoryConfig() {
        return memoryConfig;
    }

    public TraceRun traceRun() {
        return traceRun;
    }

    /**
     * 接收模型增量，累计文本和 Token 用量，并向所有 SSE 观察者发布 CHUNK 事件。
     */
    public synchronized void acceptChunk(ChatStreamChunk chunk) {
        if (terminal) {
            // 终态之后到达的迟到分片必须丢弃，避免覆盖已经持久化的最终内容。
            return;
        }
        if (chunk.hasUsage()) {
            // 部分供应商只在最后一个无文本分片中返回 Usage，因此用量处理不能依赖 content 非空。
            tokenUsage = chunk.usage();
        }
        if (CharSequenceUtil.isEmpty(chunk.content())) {
            // 仅携带 Usage 的空文本分片已经完成用量更新，无需推送无意义的文本事件。
            return;
        }
        answer.append(chunk.content());
        ChatGenerationEventBO event = ChatGenerationEventBO.chunk(++sequence, chunk.content());
        eventRepo.append(id(), event);
        eventSink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 接收 Agent 模型文本事件。
     */
    @Override
    public void publishText(ChatStreamChunk chunk) {
        acceptChunk(chunk);
    }

    /**
     * 发布工具执行状态；工具事件不参与回答正文累计。
     */
    @Override
    public synchronized void publishTool(AgentToolExecutionEvent event) {
        if (terminal) {
            return;
        }
        ChatGenerationEventBO generationEvent = ChatGenerationEventBO.tool(++sequence, event);
        eventRepo.append(id(), generationEvent);
        eventSink.emitNext(generationEvent, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 发布不包含模型思维链的 Agent 阶段进度；该事件不参与回答正文累计。
     */
    @Override
    public synchronized void publishProgress(AgentProgressEvent event) {
        if (terminal) {
            return;
        }
        ChatGenerationEventBO generationEvent = ChatGenerationEventBO.progress(++sequence, event);
        eventRepo.append(id(), generationEvent);
        eventSink.emitNext(generationEvent, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    @Override
    public synchronized void publishClarification(ChatClarificationEventBO event) {
        if (terminal) {
            return;
        }
        event.setSequence(++sequence);
        ChatGenerationEventBO generationEvent = ChatGenerationEventBO.clarification(sequence, event);
        eventRepo.append(id(), generationEvent);
        eventSink.emitNext(generationEvent, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 发布持久化 HITL 等待事件；事件不包含原始 Tool 参数。
     */
    @Override
    public synchronized void publishInteractionRequired(ChatInteractionEventBO event) {
        if (terminal) {
            return;
        }
        ChatGenerationEventBO generationEvent = ChatGenerationEventBO.interaction(
                ++sequence, ChatGenerationEventType.INTERACTION_REQUIRED, event);
        eventRepo.append(id(), generationEvent);
        eventSink.emitNext(generationEvent, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 发布人工决策已完成事件，随后同一运行继续输出进度。
     */
    @Override
    public synchronized void publishInteractionResolved(ChatInteractionEventBO event) {
        if (terminal) {
            return;
        }
        ChatGenerationEventBO generationEvent = ChatGenerationEventBO.interaction(
                ++sequence, ChatGenerationEventType.INTERACTION_RESOLVED, event);
        eventRepo.append(id(), generationEvent);
        eventSink.emitNext(generationEvent, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 结束当前观察流但不把 Assistant 推进为业务终态，恢复任务将继续使用同一消息。
     */
    public synchronized boolean pauseForHuman() {
        if (terminal) {
            return false;
        }
        terminal = true;
        eventSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        return true;
    }

    @Override
    public synchronized void publishExecution(ChatExecutionTimelineEventBO event) {
        if (terminal) {
            return;
        }
        ChatGenerationEventType type = switch (event.getEventType()) {
            case PLANNING -> ChatGenerationEventType.PLANNING;
            case PLAN_CREATED -> ChatGenerationEventType.PLAN;
            case REPLANNING -> ChatGenerationEventType.REPLANNING;
            default -> ChatGenerationEventType.STEP;
        };
        ChatGenerationEventBO generationEvent = ChatGenerationEventBO.execution(++sequence, type, event);
        eventRepo.append(id(), generationEvent);
        eventSink.emitNext(generationEvent, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 发布计划步骤文本增量；该事件不参与最终Assistant正文累计。
     */
    @Override
    public synchronized void publishStepOutput(ChatStepOutputEventBO event) {
        if (terminal || event == null || CharSequenceUtil.isEmpty(event.content())) {
            return;
        }
        ChatGenerationEventBO generationEvent = ChatGenerationEventBO.stepOutput(++sequence, event);
        eventRepo.append(id(), generationEvent);
        eventSink.emitNext(generationEvent, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    @Override
    public synchronized boolean isCancelled() {
        return terminal;
    }

    /**
     * Workflow 完成时校准最终回答和 Token 用量。
     *
     * <p>正常情况下实时文本事件已经组成最终回答；如果 Workflow 对输出做了转换，
     * 则发布 SNAPSHOT 让前端以最终结果覆盖增量文本。</p>
     */
    public synchronized void reconcileFinalResult(String finalAnswer, TokenUsage usage) {
        tokenUsage = Objects.requireNonNullElseGet(usage, TokenUsage::empty);
        if (Objects.equals(answer.toString(), finalAnswer)) {
            return;
        }
        answer.setLength(0);
        answer.append(finalAnswer);
        eventSink.emitNext(ChatGenerationEventBO.snapshot(finalAnswer), Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 返回指定增量序号之后的事件流。
     * 终态和 SNAPSHOT 事件不使用增量序号，因此始终保留。
     */
    public Flux<ChatGenerationEventBO> eventsAfter(long afterSequence) {
        return eventSink.asFlux().filter(event -> event.sequence() == 0 || event.sequence() > afterSequence);
    }

    /**
     * 原子地抢占任务终态处理权，返回 false 表示其他线程已经先一步结束任务。
     */
    public synchronized boolean beginTerminal() {
        if (terminal) {
            return false;
        }
        terminal = true;
        return true;
    }

    /**
     * 判断任务是否已被完成、失败、取消或中断中的某一终态抢占。
     */
    public synchronized boolean isTerminal() {
        return terminal;
    }

    /**
     * 获取当前已经累计的完整或部分回答快照。
     */
    public synchronized String answer() {
        return answer.toString();
    }

    /**
     * 获取当前已知的 Token 用量快照；供应商未返回时字段允许为空。
     */
    public synchronized TokenUsage tokenUsage() {
        return tokenUsage;
    }

    /**
     * 保存模型订阅。若任务在订阅对象返回前已经被取消，则立即释放该迟到订阅。
     */
    public synchronized void setSubscription(Disposable subscription) {
        modelSubscription = subscription;
        if (terminal) {
            subscription.dispose();
        }
    }

    /**
     * 主动停止上游模型生成；未建立订阅时无需处理。
     */
    public synchronized void disposeModelSubscription() {
        if (Objects.nonNull(modelSubscription)) {
            modelSubscription.dispose();
        }
    }

    /**
     * 当前实例失去分布式任务所有权时结束本机观察流，不伪造业务终态。
     * 客户端会按传输中断恢复，并从真正持有任务的实例继续接收事件。
     */
    public synchronized void detachObservers() {
        eventSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 发布唯一终态事件并关闭事件流，使 SSE 连接能够正常结束。
     */
    public void publishTerminal(ChatGenerationEventBO event) {
        eventSink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
        eventSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
