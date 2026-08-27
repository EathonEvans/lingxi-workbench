package com.lingxi.workbench.observability.service;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AiTraceRunTest {

    @Test
    void shouldPersistOnlyOneTerminalEventWhenCompletionRacesWithFailureAndCancellation() {
        AiObservationEventWriter eventWriter = mock(AiObservationEventWriter.class);
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        TraceRun traceRun = TraceRun.builder()
                .root(Observation.start("test-trace", observationRegistry))
                .observationRegistry(observationRegistry)
                .eventWriter(eventWriter)
                .traceId("trace-1")
                .spanId("span-1")
                .applicationId(1L)
                .applicationVersionId(2L)
                .assistantMessageId(3L)
                .startedAt(Instant.now())
                .build();

        traceRun.complete(TokenUsage.empty(), null);
        traceRun.fail(new IllegalStateException("late failure"));
        traceRun.cancel("late cancellation");

        verify(eventWriter, times(1)).append(any(AiObservationEventEntity.class));
    }
}
