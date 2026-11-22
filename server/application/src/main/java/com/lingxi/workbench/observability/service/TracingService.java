package com.lingxi.workbench.observability.service;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 创建 Application Chat 根 Trace，并绑定安全的筛选维度。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TracingService {

    private final ObservationRegistry observationRegistry;
    private final AiObservationEventWriter eventWriter;

    public TraceRun start(AppRuntimeSnapshot application, ChatTurnBO turn, Long userId) {
        SpanContext parentContext = Span.current().getSpanContext();
        Observation root = null;
        try {
            root = createRoot(application, turn, userId);
            SpanContext context;
            try (Observation.Scope ignored = root.openScope()) {
                context = Span.current().getSpanContext();
            }
            TraceRun traceRun = TraceRun.builder()
                    .root(root)
                    .observationRegistry(observationRegistry)
                    .eventWriter(eventWriter)
                    .traceId(context.getTraceId())
                    .spanId(context.getSpanId())
                    .parentSpanId(Optional.of(parentContext)
                            .filter(SpanContext::isValid)
                            .map(SpanContext::getSpanId)
                            .orElse(null))
                    .applicationId(application.applicationId())
                    .applicationVersionId(application.versionId())
                    .assistantMessageId(turn.assistantMessageId())
                    .startedAt(java.time.Instant.now())
                    .build();
            eventWriter.append(traceRun.startedEvent());
            return traceRun;
        } catch (RuntimeException exception) {
            if (root != null) {
                root.error(exception);
                root.stop();
            }
            log.warn("Agent链路观测初始化失败，当前请求降级为无操作Trace。applicationId={}，assistantMessageId={}", application.applicationId(), turn.assistantMessageId(), exception);
            return TraceRun.disabled();
        }
    }

    private Observation createRoot(AppRuntimeSnapshot application, ChatTurnBO turn, Long userId) {
        return Observation.createNotStarted("lingxi.ai.run", observationRegistry)
                .contextualName("application-chat")
                .lowCardinalityKeyValue("lingxi.application.code", application.applicationCode())
                .lowCardinalityKeyValue("langfuse.trace.name", "application-chat")
                .highCardinalityKeyValue("user.id", String.valueOf(userId))
                .highCardinalityKeyValue("session.id", String.valueOf(turn.conversationId()))
                .highCardinalityKeyValue("lingxi.application.id", String.valueOf(application.applicationId()))
                .highCardinalityKeyValue("lingxi.application.version.id", String.valueOf(application.versionId()))
                .highCardinalityKeyValue("lingxi.assistant.message.id", String.valueOf(turn.assistantMessageId()))
                .start();
    }
}
