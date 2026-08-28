package com.lingxi.workbench.observability.config;

import com.lingxi.workbench.framework.json.JSONObject;
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
import java.util.Set;

/**
 * 将阶段、最终执行和工具调用 observation 投影为管理端可查询的脱敏事件。
 */
@Component
@RequiredArgsConstructor
public class AiRuntimeObservationHandler implements ObservationHandler<Observation.Context> {

    private static final String STAGE_OBSERVATION_NAME = "lingxi.ai.stage";
    private static final String EXECUTION_OBSERVATION_NAME = "lingxi.ai.execution";
    private static final String TOOL_OBSERVATION_NAME = "spring.ai.tool";
    private static final String RUNTIME_PHASE_OBSERVATION_NAME = "lingxi.ai.runtime.phase";
    private static final String STARTED_AT_KEY = AiRuntimeObservationHandler.class.getName() + ".startedAt";
    private static final Set<String> SUPPORTED_NAMES = Set.of(
            STAGE_OBSERVATION_NAME, EXECUTION_OBSERVATION_NAME, TOOL_OBSERVATION_NAME,
            RUNTIME_PHASE_OBSERVATION_NAME);

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
        event.setEventType(eventType(context.getName()));
        event.setStage(stage(context));
        event.setApplicationId(longValue(context, "lingxi.application.id"));
        event.setRunId(value(context, "lingxi.run.id"));
        event.setModelCode(value(context, "lingxi.runtime.model.code"));
        event.setPromptTokens(integerValue(context, "lingxi.runtime.model.input_tokens"));
        event.setCompletionTokens(integerValue(context, "lingxi.runtime.model.output_tokens"));
        event.setLatencyMs(elapsedMillis(context));
        event.setStatus(status(context));
        event.setMetadata(metadata(context));
        eventWriter.append(event);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        // Web 请求等通用 Observation 在 Handler 匹配阶段可能尚未设置名称，必须安全拒绝而非影响请求链路。
        return Objects.nonNull(context.getName()) && SUPPORTED_NAMES.contains(context.getName());
    }

    private static AiObservationEventType eventType(String observationName) {
        return switch (observationName) {
            case STAGE_OBSERVATION_NAME -> AiObservationEventType.STAGE_COMPLETED;
            case EXECUTION_OBSERVATION_NAME -> AiObservationEventType.EXECUTION_COMPLETED;
            case TOOL_OBSERVATION_NAME -> AiObservationEventType.TOOL_CALL;
            case RUNTIME_PHASE_OBSERVATION_NAME -> AiObservationEventType.RUNTIME_PHASE;
            default -> throw new IllegalArgumentException("不支持的观测类型：" + observationName);
        };
    }

    private static AiObservationStage stage(Observation.Context context) {
        return Optional.ofNullable(value(context, "lingxi.stage"))
                .map(AiObservationStage::fromCode)
                .orElse(AiObservationStage.EXECUTION);
    }

    private static JSONObject metadata(Observation.Context context) {
        JSONObject metadata = new JSONObject();
        putIfPresent(metadata, "executionMode", value(context, "lingxi.execution.mode"));
        putIfPresent(metadata, "toolName", value(context, "spring.ai.tool.definition.name"));
        putIfPresent(metadata, "toolType", value(context, "spring.ai.tool.type"));
        putIfPresent(metadata, "runtimePhase", value(context, "lingxi.runtime.phase"));
        putIfPresent(metadata, "runtimeOperation", value(context, "lingxi.runtime.operation"));
        putIfPresent(metadata, "runtimeOperationName", value(context, "lingxi.runtime.operation.name"));
        putIfPresent(metadata, "agentId", value(context, "lingxi.agent.id"));
        putIfPresent(metadata, "agentVersionId", value(context, "lingxi.agent.version.id"));
        putIfPresent(metadata, "invocationId", value(context, "lingxi.runtime.invocation.id"));
        putIfPresent(metadata, "modelInstanceId", value(context, "lingxi.runtime.model.instance_id"));
        putIfPresent(metadata, "modelCallNo", value(context, "lingxi.runtime.model.call_no"));
        putIfPresent(metadata, "modelFirstResponseMs",
                value(context, "lingxi.runtime.model.first_response_ms"));
        putIfPresent(metadata, "runtimeErrorCode", value(context, "lingxi.runtime.error.code"));
        if (Objects.nonNull(context.getError())) {
            metadata.put("errorType", context.getError().getClass().getSimpleName());
        }
        return metadata;
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
