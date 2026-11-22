package com.lingxi.workbench.observability.config;

import com.lingxi.workbench.observability.common.enums.AiObservationEventType;
import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import com.lingxi.workbench.observability.service.AiObservationEventWriter;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiRuntimeObservationHandlerTest {

    @Test
    void shouldIgnoreUnnamedWebObservationContext() {
        AiObservationEventWriter writer = mock(AiObservationEventWriter.class);
        AiRuntimeObservationHandler handler = new AiRuntimeObservationHandler(writer);

        boolean supported = handler.supportsContext(new Observation.Context());

        assertThat(supported).isFalse();
        verifyNoInteractions(writer);
    }

    @Test
    void shouldPersistToolNameWithoutArgumentsOrResult() {
        AiObservationEventWriter writer = mock(AiObservationEventWriter.class);
        AiRuntimeObservationHandler handler = new AiRuntimeObservationHandler(writer);
        Observation.Context context = tracedContext("spring.ai.tool");
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.application.id", "20"));
        context.addHighCardinalityKeyValue(KeyValue.of("spring.ai.tool.call.arguments", "private"));
        context.addLowCardinalityKeyValue(KeyValue.of("spring.ai.tool.definition.name", "knowledge_search"));

        handler.onStart(context);
        handler.onStop(context);

        ArgumentCaptor<AiObservationEventEntity> captor = ArgumentCaptor.forClass(AiObservationEventEntity.class);
        verify(writer).append(captor.capture());
        AiObservationEventEntity event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AiObservationEventType.TOOL_CALL);
        assertThat(event.getApplicationId()).isEqualTo(20L);
        assertThat(event.getMetadata().getString("toolName")).isEqualTo("knowledge_search");
        assertThat(event.getMetadata()).doesNotContainKeys("arguments", "result");
    }

    @Test
    void shouldPersistExecutionMode() {
        AiObservationEventWriter writer = mock(AiObservationEventWriter.class);
        AiRuntimeObservationHandler handler = new AiRuntimeObservationHandler(writer);
        Observation.Context context = tracedContext("lingxi.ai.execution");
        context.addLowCardinalityKeyValue(KeyValue.of("lingxi.execution.mode", "PLAN_EXECUTE"));

        handler.onStart(context);
        handler.onStop(context);

        ArgumentCaptor<AiObservationEventEntity> captor = ArgumentCaptor.forClass(AiObservationEventEntity.class);
        verify(writer).append(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AiObservationEventType.EXECUTION_COMPLETED);
        assertThat(captor.getValue().getMetadata().getString("executionMode")).isEqualTo("PLAN_EXECUTE");
    }

    @Test
    void shouldPersistSanitizedRuntimePhase() {
        AiObservationEventWriter writer = mock(AiObservationEventWriter.class);
        AiRuntimeObservationHandler handler = new AiRuntimeObservationHandler(writer);
        Observation.Context context = tracedContext("lingxi.ai.runtime.phase");
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.run.id", "run-1"));
        context.addLowCardinalityKeyValue(KeyValue.of("lingxi.runtime.phase", "COORDINATION"));
        context.addLowCardinalityKeyValue(KeyValue.of("lingxi.runtime.operation", "wait_async_results"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.operation.name", "结果等待"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.invocation.id", "invocation-1"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.model.first_response_ms", "730"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.model.instance_id", "9"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.model.code", "test-model"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.model.call_no", "2"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.model.input_tokens", "1200"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.model.output_tokens", "240"));
        context.addHighCardinalityKeyValue(KeyValue.of("lingxi.runtime.error.code", "AGENTSCOPE_COORDINATION_ERROR"));
        context.addHighCardinalityKeyValue(KeyValue.of("agentscope.tool.arguments", "private"));
        context.setError(new IllegalStateException("internal"));

        handler.onStart(context);
        handler.onStop(context);

        ArgumentCaptor<AiObservationEventEntity> captor = ArgumentCaptor.forClass(AiObservationEventEntity.class);
        verify(writer).append(captor.capture());
        AiObservationEventEntity event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(AiObservationEventType.RUNTIME_PHASE);
        assertThat(event.getRunId()).isEqualTo("run-1");
        assertThat(event.getMetadata().getString("runtimePhase")).isEqualTo("COORDINATION");
        assertThat(event.getMetadata().getString("runtimeOperation")).isEqualTo("wait_async_results");
        assertThat(event.getMetadata().getString("runtimeOperationName")).isEqualTo("结果等待");
        assertThat(event.getMetadata().getLong("modelFirstResponseMs")).isEqualTo(730L);
        assertThat(event.getMetadata().getLong("modelInstanceId")).isEqualTo(9L);
        assertThat(event.getMetadata().getInteger("modelCallNo")).isEqualTo(2);
        assertThat(event.getModelCode()).isEqualTo("test-model");
        assertThat(event.getPromptTokens()).isEqualTo(1200);
        assertThat(event.getCompletionTokens()).isEqualTo(240);
        assertThat(event.getMetadata().getString("runtimeErrorCode")).isEqualTo("AGENTSCOPE_COORDINATION_ERROR");
        assertThat(event.getMetadata()).doesNotContainKeys("arguments", "result");
    }

    private static Observation.Context tracedContext(String name) {
        Observation.Context context = new Observation.Context();
        context.setName(name);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace-1");
        when(traceContext.spanId()).thenReturn("span-1");
        when(traceContext.parentId()).thenReturn("parent-1");
        TracingObservationHandler.TracingContext tracing = new TracingObservationHandler.TracingContext();
        tracing.setSpan(span);
        context.put(TracingObservationHandler.TracingContext.class, tracing);
        return context;
    }
}
