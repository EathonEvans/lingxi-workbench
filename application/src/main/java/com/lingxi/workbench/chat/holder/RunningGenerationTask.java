package com.lingxi.workbench.chat.holder;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;

/**
 * 单个模型生成任务及其进程内可重放事件。
 *
 * <p>该对象只存在于当前服务进程，不承担最终持久化职责。所有可能由不同线程调用的状态方法均进行同步，
 * 防止模型回调、用户取消和应用关闭同时修改回答内容或重复进入终态。</p>
 */
public final class RunningGenerationTask {

    /**
     * 对应数据库中的 Assistant 消息主键。
     */
    private final Long id;
    /**
     * 按到达顺序累积模型文本增量，终态时一次性保存完整或部分回答。
     */
    private final StringBuilder answer = new StringBuilder();
    /**
     * 进程内事件总线。replay().all() 允许较晚建立的 SSE 订阅者重放本进程已经产生的事件。
     */
    private final Sinks.Many<ChatGenerationEventBO> eventSink = Sinks.many().replay().all();

    /**
     * CHUNK 事件的单调递增序号，用于客户端断线后按序号续传。
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
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;

    public RunningGenerationTask(Long id) {
        this.id = id;
    }

    public Long id() {
        return id;
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
            inputTokens = chunk.inputTokens();
            outputTokens = chunk.outputTokens();
            totalTokens = chunk.totalTokens();
        }
        if (CharSequenceUtil.isEmpty(chunk.content())) {
            // 仅携带 Usage 的空文本分片已经完成用量更新，无需推送无意义的文本事件。
            return;
        }
        answer.append(chunk.content());
        eventSink.emitNext(ChatGenerationEventBO.chunk(++sequence, chunk.content()), Sinks.EmitFailureHandler.FAIL_FAST);
    }

    /**
     * 返回指定 CHUNK 序号之后的事件流。
     * 终态和 SNAPSHOT 事件不使用增量序号，因此始终保留。
     */
    public Flux<ChatGenerationEventBO> eventsAfter(long afterSequence) {
        return eventSink.asFlux().filter(event -> event.type() != ChatGenerationEventType.CHUNK || event.sequence() > afterSequence);
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
     * 获取当前已经累计的完整或部分回答快照。
     */
    public synchronized String answer() {
        return answer.toString();
    }

    /**
     * 获取当前已知的 Token 用量快照；供应商未返回时字段允许为空。
     */
    public synchronized MessageTokenUsageBO tokenUsage() {
        return new MessageTokenUsageBO(inputTokens, outputTokens, totalTokens);
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
     * 发布唯一终态事件并关闭事件流，使 SSE 连接能够正常结束。
     */
    public void publishTerminal(ChatGenerationEventBO event) {
        eventSink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
        eventSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
    }
}