package com.lingxi.workbench.observability.config;

import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import com.lingxi.workbench.observability.service.AiObservationEventWriter;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelObservationHandlerTest {

    @Test
    void shouldOnlyPersistModelCallObservation() {
        AiModelObservationHandler handler = new AiModelObservationHandler(mock(AiObservationEventWriter.class));

        assertFalse(handler.supportsContext(context("spring.ai.chat.client")));
        assertFalse(handler.supportsContext(context("spring.ai.advisor")));
    }

    @Test
    void shouldPersistSafeModelMetadataAndTraceIds() {
        AiObservationEventWriter writer = mock(AiObservationEventWriter.class);
        AiModelObservationHandler handler = new AiModelObservationHandler(writer);
        Observation.Context context = new Observation.Context();
        context.setName("gen_ai.client.operation");
        context.addHighCardinalityKeyValue(io.micrometer.common.KeyValue.of("lingxi.application.id", "20"));
        context.addHighCardinalityKeyValue(io.micrometer.common.KeyValue.of("gen_ai.request.model", "deepseek-chat"));
        context.addHighCardinalityKeyValue(io.micrometer.common.KeyValue.of("gen_ai.usage.input_tokens", "10"));
        context.addHighCardinalityKeyValue(io.micrometer.common.KeyValue.of("gen_ai.usage.output_tokens", "4"));
        context.addLowCardinalityKeyValue(io.micrometer.common.KeyValue.of("lingxi.stage", "EXECUTION"));
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace-1");
        when(traceContext.spanId()).thenReturn("span-1");
        when(traceContext.parentId()).thenReturn("parent-1");
        TracingObservationHandler.TracingContext tracing = new TracingObservationHandler.TracingContext();
        tracing.setSpan(span);
        context.put(TracingObservationHandler.TracingContext.class, tracing);

        handler.onStart(context);
        handler.onStop(context);

        ArgumentCaptor<AiObservationEventEntity> captor = ArgumentCaptor.forClass(AiObservationEventEntity.class);
        verify(writer).append(captor.capture());
        AiObservationEventEntity event = captor.getValue();
        assertEquals("trace-1", event.getTraceId());
        assertEquals(20L, event.getApplicationId());
        assertEquals("deepseek-chat", event.getModelCode());
        assertEquals(10, event.getPromptTokens());
        assertEquals(4, event.getCompletionTokens());
    }

    private static Observation.Context context(String name) {
        Observation.Context context = new Observation.Context();
        context.setName(name);
        return context;
    }
}
