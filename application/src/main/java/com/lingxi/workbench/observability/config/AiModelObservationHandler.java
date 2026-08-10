package com.lingxi.workbench.observability.config;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.observability.common.enums.AiObservationEventType;
import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import com.lingxi.workbench.observability.common.enums.AiObservationStatus;
import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import com.lingxi.workbench.observability.service.AiObservationEventWriter;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/** 将 Spring AI 模型 observation 投影为轻量数据库事件。 */
@Component
@RequiredArgsConstructor
public class AiModelObservationHandler implements ObservationHandler<Observation.Context> {

    private static final String MODEL_OBSERVATION_NAME = "gen_ai.client.operation";
    private static final String STARTED_AT_KEY = AiModelObservationHandler.class.getName() + ".startedAt";

    private final AiObservationEventWriter eventWriter;

    @Override
    public void onStart(Observation.Context context) {
        context.put(STARTED_AT_KEY, System.nanoTime());
    }

    @Override
    public void onStop(Observation.Context context) {
        TracingObservationHandler.TracingContext tracingContext = context.get(TracingObservationHandler.TracingContext.class);
        if (Objects.isNull(tracingContext) || Objects.isNull(tracingContext.getSpan())) {
            return;
        }
        Span span = tracingContext.getSpan();
        AiObservationEventEntity event = new AiObservationEventEntity();
        event.setTraceId(span.context().traceId());
        event.setSpanId(span.context().spanId());
        event.setParentSpanId(span.context().parentId());
        event.setEventType(AiObservationEventType.MODEL_CALL);
        event.setStage(stage(context));
        event.setApplicationId(longValue(context, "lingxi.application.id"));
        event.setRunId(value(context, "lingxi.run.id"));
        event.setModelCode(value(context, "gen_ai.request.model"));
        event.setPromptTokens(integerValue(context, "gen_ai.usage.input_tokens"));
        event.setCompletionTokens(integerValue(context, "gen_ai.usage.output_tokens"));
        event.setLatencyMs(elapsedMillis(context));
        event.setStatus(status(context));
        event.setMetadata(metadata(context));
        eventWriter.append(event);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return MODEL_OBSERVATION_NAME.equals(context.getName());
    }

    private static JSONObject metadata(Observation.Context context) {
        JSONObject metadata = new JSONObject();
        putIfPresent(metadata, "finishReasons", value(context, "gen_ai.response.finish_reasons"));
        putIfPresent(metadata, "responseModel", value(context, "gen_ai.response.model"));
        putIfPresent(metadata, "assistantMessageId", value(context, "lingxi.assistant.message.id"));
        if (Objects.nonNull(context.getError())) {
            metadata.put("errorType", context.getError().getClass().getSimpleName());
        }
        return metadata;
    }

    private static AiObservationStage stage(Observation.Context context) {
        String value = value(context, "lingxi.stage");
        if (Objects.isNull(value)) {
            return AiObservationStage.EXECUTION;
        }
        return AiObservationStage.fromCode(value);
    }

    private static long elapsedMillis(Observation.Context context) {
        Long startedAt = context.get(STARTED_AT_KEY);
        return Optional.ofNullable(startedAt)
                .map(value -> (System.nanoTime() - value) / 1_000_000)
                .orElse(0L);
    }

    private static AiObservationStatus status(Observation.Context context) {
        return Optional.ofNullable(context.getError())
                .map(ignored -> AiObservationStatus.ERROR)
                .orElse(AiObservationStatus.SUCCESS);
    }

    private static Long longValue(Observation.Context context, String key) {
        return Optional.ofNullable(value(context, key)).map(Long::valueOf).orElse(null);
    }

    private static Integer integerValue(Observation.Context context, String key) {
        return Optional.ofNullable(value(context, key)).map(Integer::valueOf).orElse(null);
    }

    private static String value(Observation.Context context, String key) {
        KeyValue high = context.getHighCardinalityKeyValue(key);
        if (Objects.nonNull(high)) {
            return high.getValue();
        }
        return Optional.ofNullable(context.getLowCardinalityKeyValue(key)).map(KeyValue::getValue).orElse(null);
    }

    private static void putIfPresent(JSONObject metadata, String key, String value) {
        if (Objects.nonNull(value)) {
            metadata.put(key, value);
        }
    }
}
