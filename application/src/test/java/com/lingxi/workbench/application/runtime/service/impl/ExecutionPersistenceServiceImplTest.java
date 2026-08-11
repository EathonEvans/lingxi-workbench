package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.repo.ExecutionEventRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionPlanSnapshotRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionStepAttemptRepo;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionPersistenceServiceImplTest {

    @Test
    void shouldPersistLockedRuntimeMetadataAndSequencedEvent() {
        ExecutionRunRepo runRepo = mock(ExecutionRunRepo.class);
        ExecutionEventRepo eventRepo = mock(ExecutionEventRepo.class);
        ExecutionPersistenceServiceImpl service = service(runRepo, eventRepo);
        AppRuntimeSnapshot application = mock(AppRuntimeSnapshot.class);
        when(application.applicationId()).thenReturn(10L);
        when(application.versionId()).thenReturn(11L);

        String runId = service.startRun(ExecutionRunStartCommand.builder()
                .application(application)
                .turn(new ChatTurnBO(20L, 21L, 22L, 1))
                .userId(23L)
                .plannerPolicy(plannerPolicy())
                .build());

        ArgumentCaptor<ExecutionRunEntity> runCaptor = ArgumentCaptor.forClass(ExecutionRunEntity.class);
        verify(runRepo).insert(runCaptor.capture());
        assertNotNull(runId);
        assertEquals(runId, runCaptor.getValue().getRunId());
        assertEquals(22L, runCaptor.getValue().getAssistantMessageId());
        assertEquals(11L, runCaptor.getValue().getApplicationVersionId());
        assertEquals(31L, runCaptor.getValue().getPlannerModelInstanceId());
        assertEquals(32L, runCaptor.getValue().getPlannerPromptVersionId());

        when(runRepo.lockByRunId(runId)).thenReturn(Optional.of(runCaptor.getValue()));
        when(eventRepo.nextSequence(runId)).thenReturn(4L);
        service.appendEvent(ExecutionEventCommand.builder()
                .runId(runId)
                .eventType(ExecutionEventType.STEP_STARTED)
                .stepId("collect")
                .message("开始收集")
                .build());

        ArgumentCaptor<ExecutionEventEntity> eventCaptor = ArgumentCaptor.forClass(ExecutionEventEntity.class);
        verify(eventRepo).insert(eventCaptor.capture());
        assertEquals(4L, eventCaptor.getValue().getSequenceNo());
        assertEquals(ExecutionEventType.STEP_STARTED, eventCaptor.getValue().getEventType());
        assertNotNull(eventCaptor.getValue().getOccurredAt());
    }

    @Test
    void shouldRejectNonTerminalStepAttempt() {
        ExecutionPersistenceServiceImpl service = service(mock(ExecutionRunRepo.class), mock(ExecutionEventRepo.class));
        ExecutionStepAttemptCommand command = ExecutionStepAttemptCommand.builder()
                .runId("run-1")
                .planNo(1)
                .attemptNo(1)
                .step(PlanStep.builder().id("collect").name("收集资料").capabilityCode("agent").build())
                .status(ExecutionStepStatus.RUNNING)
                .startedAt(OffsetDateTime.now())
                .build();

        assertThrows(BusinessException.class, () -> service.appendStepAttempt(command));
    }

    private static ExecutionPersistenceServiceImpl service(ExecutionRunRepo runRepo, ExecutionEventRepo eventRepo) {
        return new ExecutionPersistenceServiceImpl(runRepo, mock(ExecutionPlanSnapshotRepo.class),
                mock(ExecutionStepAttemptRepo.class), eventRepo, new ExecutionDecisionChecksum());
    }

    private static PlannerPolicy plannerPolicy() {
        return PlannerPolicy.builder()
                .enabled(true)
                .chatModelInstanceId(31L)
                .promptVersionId(32L)
                .maxSteps(4)
                .maxReplans(1)
                .build();
    }
}
