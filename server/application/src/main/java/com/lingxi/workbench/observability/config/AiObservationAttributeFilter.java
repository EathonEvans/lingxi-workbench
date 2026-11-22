package com.lingxi.workbench.observability.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 将安全的 Trace 维度复制到子 span。
 *
 * <p>Langfuse 按 observation 聚合筛选，user/session/application 等维度必须存在于每个 span。
 * 这里只传播标识和阶段，不传播 Prompt、回答、工具入参等敏感内容。</p>
 */
@Component
public class AiObservationAttributeFilter implements ObservationFilter {

    private static final List<String> HIGH_CARDINALITY_KEYS = List.of(
            "user.id",
            "session.id",
            "lingxi.application.id",
            "lingxi.application.version.id",
            "lingxi.assistant.message.id",
            "lingxi.run.id");
    private static final List<String> LOW_CARDINALITY_KEYS = List.of(
            "lingxi.application.code",
            "lingxi.stage",
            "lingxi.execution.mode",
            "langfuse.trace.name");

    @Override
    public Observation.Context map(Observation.Context context) {
        ObservationView parent = context.getParentObservation();
        while (Objects.nonNull(parent)) {
            Observation.ContextView parentContext = parent.getContextView();
            copyMissingHighCardinality(context, parentContext);
            copyMissingLowCardinality(context, parentContext);
            parent = parentContext.getParentObservation();
        }
        return context;
    }

    private static void copyMissingHighCardinality(Observation.Context target, Observation.ContextView source) {
        for (String key : HIGH_CARDINALITY_KEYS) {
            if (Objects.nonNull(target.getHighCardinalityKeyValue(key))) {
                continue;
            }
            KeyValue value = source.getHighCardinalityKeyValue(key);
            if (Objects.nonNull(value)) {
                target.addHighCardinalityKeyValue(value);
            }
        }
    }

    private static void copyMissingLowCardinality(Observation.Context target, Observation.ContextView source) {
        for (String key : LOW_CARDINALITY_KEYS) {
            if (Objects.nonNull(target.getLowCardinalityKeyValue(key))) {
                continue;
            }
            KeyValue value = source.getLowCardinalityKeyValue(key);
            if (Objects.nonNull(value)) {
                target.addLowCardinalityKeyValue(value);
            }
        }
    }
}
