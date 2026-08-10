package com.lingxi.workbench.observability.service;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.observability.model.entity.AiObservationEventEntity;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTracingServiceTest {

    @Test
    void shouldDegradeToNoopTraceWhenObservationInitializationFails() {
        AiObservationEventWriter eventWriter = mock(AiObservationEventWriter.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(eventWriter).append(any(AiObservationEventEntity.class));
        AiTracingService service = new AiTracingService(ObservationRegistry.create(), eventWriter);
        AppRuntimeSnapshot application = mock(AppRuntimeSnapshot.class);
        when(application.applicationId()).thenReturn(1L);
        when(application.applicationCode()).thenReturn("career_assistant");
        when(application.versionId()).thenReturn(2L);
        ChatTurnBO turn = new ChatTurnBO(3L, 4L, 5L, 1);

        AiTraceRun traceRun = service.start(application, turn, 6L);

        assertEquals("continued", traceRun.scoped(() -> "continued"));
        assertDoesNotThrow(() -> traceRun.complete(TokenUsage.empty(), null));
        verify(eventWriter).append(any(AiObservationEventEntity.class));
    }
}
