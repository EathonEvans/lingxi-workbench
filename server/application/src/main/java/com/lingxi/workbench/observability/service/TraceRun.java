package com.lingxi.workbench.observability.service;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.observability.common.enums.AiObservationEventType;
import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import com.lingxi.workbench.observability.common.enums.AiObservationStatus;
import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 一轮后台 Agent 生成任务的根 Trace 生命周期。
 *
 * <p>根 Observation 可以跨线程传递，但 Scope 只允许在当前执行线程短暂打开，
 * 不共享可变 span 栈，也不依赖 HTTP 请求线程的 ThreadLocal 生命周期。</p>
 */
@Builder(access = lombok.AccessLevel.PACKAGE)
@Slf4j
public final class TraceRun {

    private final Observation root;
    private final ObservationRegistry observationRegistry;
    private final AiObservationEventWriter eventWriter;
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final Long applicationId;
    private final Long applicationVersionId;
    private final Long assistantMessageId;
    private final Instant startedAt;
    @Builder.Default
    private final boolean enabled = true;
    @Builder.Default
    private final AtomicBoolean finished = new AtomicBoolean();

    /**
     * 观测基础设施不可用时返回无操作 Trace，保证聊天主链路继续执行。
     */
    static TraceRun disabled() {
        return TraceRun.builder()
                .root(Observation.NOOP)
                .observationRegistry(ObservationRegistry.NOOP)
                .enabled(false)
                .build();
    }

    /**
     * 在根 Observation Scope 内执行任务，使内部阶段和 Spring AI span 自动建立父子关系。
     */
    public <T> T scoped(Supplier<T> action) {
        if (!enabled) {
            return action.get();
        }
        try (Observation.Scope ignored = root.openScope()) {
            return action.get();
        }
    }

    public void scoped(Runnable action) {
        scoped(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 在根链路下记录一个业务阶段，并将异常状态同步到阶段 span。
     */
    public <T> T observe(AiObservationStage stage, Supplier<T> action) {
        if (!enabled) {
            return action.get();
        }
        Observation observation = Observation.createNotStarted("lingxi.ai.stage", observationRegistry)
                .contextualName(stage.getCode().toLowerCase())
                .parentObservation(root)
                .lowCardinalityKeyValue("lingxi.stage", stage.getCode());
        return observation.observe(action);
    }

    public void complete(TokenUsage usage, Long modelInstanceId) {
        if (!enabled) {
            return;
        }
        JSONObject metadata = JSONObject.of(
                "applicationVersionId", applicationVersionId,
                "assistantMessageId", assistantMessageId);
        if (Objects.nonNull(modelInstanceId)) {
            metadata.put("modelInstanceId", modelInstanceId);
        }
        finish(AiObservationEventType.RUN_COMPLETED, AiObservationStatus.SUCCESS, usage, metadata, null);
    }

    public void fail(Throwable exception) {
        if (!enabled) {
            return;
        }
        JSONObject metadata = JSONObject.of(
                "applicationVersionId", applicationVersionId,
                "assistantMessageId", assistantMessageId,
                "errorType", exception.getClass().getSimpleName());
        finish(AiObservationEventType.RUN_FAILED, AiObservationStatus.ERROR, TokenUsage.empty(), metadata, exception);
    }

    public void cancel(String reason) {
        if (!enabled) {
            return;
        }
        JSONObject metadata = JSONObject.of(
                "applicationVersionId", applicationVersionId,
                "assistantMessageId", assistantMessageId,
                "reason", reason);
        finish(AiObservationEventType.RUN_FAILED, AiObservationStatus.ERROR, TokenUsage.empty(), metadata, null);
    }

    public String traceId() {
        return traceId;
    }

    private void appendTerminal(AiObservationEventType type, AiObservationStatus status, TokenUsage usage, JSONObject metadata) {
        AiObservationEventEntity event = baseEvent(type, status);
        event.setStage(AiObservationStage.TERMINAL);
        event.setPromptTokens(usage.inputTokens());
        event.setCompletionTokens(usage.outputTokens());
        event.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
        event.setMetadata(metadata);
        eventWriter.append(event);
    }

    private void finish(AiObservationEventType type, AiObservationStatus status, TokenUsage usage,
                        JSONObject metadata, Throwable error) {
        if (!enabled || !finished.compareAndSet(false, true)) {
            return;
        }
        try {
            appendTerminal(type, status, usage, metadata);
        } catch (RuntimeException exception) {
            log.warn("Agent观测终态写入失败，traceId={}，eventType={}", traceId, type, exception);
        }
        try {
            if (Objects.nonNull(error)) {
                root.error(error);
            }
            root.stop();
        } catch (RuntimeException exception) {
            log.warn("Agent根Trace关闭失败，traceId={}", traceId, exception);
        }
    }

    AiObservationEventEntity startedEvent() {
        AiObservationEventEntity event = baseEvent(AiObservationEventType.RUN_STARTED, AiObservationStatus.SUCCESS);
        event.setStage(AiObservationStage.MESSAGE_PERSIST);
        event.setMetadata(JSONObject.of(
                "applicationVersionId", applicationVersionId,
                "assistantMessageId", assistantMessageId));
        return event;
    }

    private AiObservationEventEntity baseEvent(AiObservationEventType type, AiObservationStatus status) {
        AiObservationEventEntity event = new AiObservationEventEntity();
        event.setTraceId(traceId);
        event.setSpanId(spanId);
        event.setParentSpanId(parentSpanId);
        event.setEventType(type);
        event.setApplicationId(applicationId);
        event.setStatus(status);
        return event;
    }
}
