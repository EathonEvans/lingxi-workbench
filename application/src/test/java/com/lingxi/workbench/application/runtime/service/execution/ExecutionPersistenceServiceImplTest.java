package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.plan.RuntimeTaskStep;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionSkillActivationCommand;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunSkillEntity;
import com.lingxi.workbench.application.runtime.repo.ExecutionEventRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunSkillRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionStepAttemptRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionTaskSnapshotRepo;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.infra.ai.agent.model.PlanPolicy;
import com.lingxi.workbench.infra.ai.skill.common.enums.SkillActivationReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import io.agentscope.core.state.Task;

import java.time.OffsetDateTime;
import java.util.List;
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

        String runId = "run-1";
        service.startRun(ExecutionRunStartCommand.builder()
                .application(application)
                .turn(new ChatTurnBO(20L, 21L, 22L, 1))
                .userId(23L)
                .runId(runId)
                .orchestratorAgent(supervisor())
                .capabilityCode("default_agent")
                .executionMode("PLAN_EXECUTE")
                .build());

        ArgumentCaptor<ExecutionRunEntity> runCaptor = ArgumentCaptor.forClass(ExecutionRunEntity.class);
        verify(runRepo).insert(runCaptor.capture());
        assertEquals(runId, runCaptor.getValue().getRunId());
        assertEquals(22L, runCaptor.getValue().getAssistantMessageId());
        assertEquals(11L, runCaptor.getValue().getApplicationVersionId());
        assertEquals(31L, runCaptor.getValue().getSupervisorModelInstanceId());
        assertEquals(32L, runCaptor.getValue().getSupervisorPromptVersionId());
        assertEquals(40L, runCaptor.getValue().getOrchestratorAgentId());
        assertEquals(41L, runCaptor.getValue().getOrchestratorAgentVersionId());

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
                .taskRevision(1)
                .attemptNo(1)
                .step(RuntimeTaskStep.builder().id("collect").name("收集资料").capabilityCode("agent").build())
                .status(ExecutionStepStatus.RUNNING)
                .startedAt(OffsetDateTime.now())
                .build();

        assertThrows(BusinessException.class, () -> service.appendStepAttempt(command));
    }

    @Test
    void shouldPersistAgentScopeTaskSnapshot() {
        ExecutionRunRepo runRepo = mock(ExecutionRunRepo.class);
        ExecutionTaskSnapshotRepo taskRepo = mock(ExecutionTaskSnapshotRepo.class);
        when(runRepo.lockByRunId("run-task")).thenReturn(Optional.of(new ExecutionRunEntity()));
        ExecutionPersistenceServiceImpl service = new ExecutionPersistenceServiceImpl(runRepo,
                taskRepo, mock(ExecutionStepAttemptRepo.class), mock(ExecutionEventRepo.class),
                mock(ExecutionRunSkillRepo.class), new ExecutionTaskChecksum());
        Task task = Task.builder().id("task-1").subject("查询资料").description("查询资料")
                .state(Task.State.IN_PROGRESS).metadata(java.util.Map.of("capabilityCode", "research")).build();

        service.appendTaskSnapshot(new com.lingxi.workbench.application.runtime.model.ExecutionTaskSnapshotCommand(
                "run-task", 1, List.of(task)));

        ArgumentCaptor<com.lingxi.workbench.application.runtime.model.entity.ExecutionTaskSnapshotEntity> captor =
                ArgumentCaptor.forClass(com.lingxi.workbench.application.runtime.model.entity.ExecutionTaskSnapshotEntity.class);
        verify(taskRepo).insert(captor.capture());
        assertEquals("run-task", captor.getValue().getRunId());
        assertEquals(1, captor.getValue().getRevision());
        assertEquals("research", captor.getValue().getTaskSnapshot().getFirst().getMetadata().get("capabilityCode"));
        assertNotNull(captor.getValue().getSnapshotChecksum());
    }

    @Test
    void shouldPersistRuntimeOwnedSequenceAndOccurrenceTime() {
        ExecutionRunRepo runRepo = mock(ExecutionRunRepo.class);
        ExecutionEventRepo eventRepo = mock(ExecutionEventRepo.class);
        when(runRepo.lockByRunId("run-runtime")).thenReturn(Optional.of(new ExecutionRunEntity()));
        when(eventRepo.nextSequence("run-runtime")).thenReturn(9L);
        ExecutionPersistenceServiceImpl service = service(runRepo, eventRepo);
        java.time.Instant occurredAt = java.time.Instant.parse("2026-08-17T14:00:00Z");

        service.appendEvent(ExecutionEventCommand.builder()
                .runId("run-runtime")
                .sequenceNo(9L)
                .occurredAt(occurredAt)
                .eventType(ExecutionEventType.MODEL_STARTED)
                .message("模型调用已开始")
                .build());

        ArgumentCaptor<ExecutionEventEntity> captor = ArgumentCaptor.forClass(ExecutionEventEntity.class);
        verify(eventRepo).insert(captor.capture());
        assertEquals(9L, captor.getValue().getSequenceNo());
        assertEquals(java.time.OffsetDateTime.ofInstant(occurredAt, java.time.ZoneOffset.UTC),
                captor.getValue().getOccurredAt());
    }

    @Test
    void shouldReturnExistingRuntimeEventIdempotently() {
        ExecutionRunRepo runRepo = mock(ExecutionRunRepo.class);
        ExecutionEventRepo eventRepo = mock(ExecutionEventRepo.class);
        when(runRepo.lockByRunId("run-runtime")).thenReturn(Optional.of(new ExecutionRunEntity()));
        ExecutionEventEntity existing = new ExecutionEventEntity();
        existing.setRunId("run-runtime");
        existing.setSequenceNo(3L);
        existing.setEventType(ExecutionEventType.MODEL_COMPLETED);
        when(eventRepo.findBySequence("run-runtime", 3L)).thenReturn(Optional.of(existing));
        ExecutionPersistenceServiceImpl service = service(runRepo, eventRepo);

        ExecutionEventEntity result = service.appendEvent(ExecutionEventCommand.builder()
                .runId("run-runtime")
                .sequenceNo(3L)
                .eventType(ExecutionEventType.MODEL_COMPLETED)
                .message("模型调用已完成")
                .build());

        assertEquals(existing, result);
        org.mockito.Mockito.verify(eventRepo, org.mockito.Mockito.never())
                .insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldPersistSkillActivationEventAndAuditFactTogether() {
        ExecutionRunRepo runRepo = mock(ExecutionRunRepo.class);
        ExecutionEventRepo eventRepo = mock(ExecutionEventRepo.class);
        ExecutionRunSkillRepo skillRepo = mock(ExecutionRunSkillRepo.class);
        when(runRepo.lockByRunId("run-skill")).thenReturn(Optional.of(new ExecutionRunEntity()));
        when(eventRepo.nextSequence("run-skill")).thenReturn(1L);
        ExecutionPersistenceServiceImpl service = new ExecutionPersistenceServiceImpl(runRepo,
                mock(ExecutionTaskSnapshotRepo.class), mock(ExecutionStepAttemptRepo.class), eventRepo,
                skillRepo, new ExecutionTaskChecksum());

        service.appendSkillActivationEvent(ExecutionEventCommand.builder()
                        .runId("run-skill").sequenceNo(1L)
                        .eventType(ExecutionEventType.SKILL_ACTIVATED).message("Skill已激活").build(),
                ExecutionSkillActivationCommand.builder().runId("run-skill").agentVersionId(20L)
                        .skillId(1L).skillVersionId(11L).skillCode("fact_check").checksum("checksum")
                        .reason(SkillActivationReason.EXPLICIT).effectiveToolCodes(List.of("search")).build());

        verify(eventRepo).insert(org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<ExecutionRunSkillEntity> skillCaptor =
                ArgumentCaptor.forClass(ExecutionRunSkillEntity.class);
        verify(skillRepo).insert(skillCaptor.capture());
        assertEquals(1L, skillCaptor.getValue().getEventSequenceNo());
        assertEquals(SkillActivationReason.EXPLICIT, skillCaptor.getValue().getActivationReason());
        assertEquals(List.of("search"), skillCaptor.getValue().getEffectiveToolCodes());
    }

    @Test
    void shouldNotDuplicateSkillAuditWhenActivationEventIsReplayed() {
        ExecutionRunRepo runRepo = mock(ExecutionRunRepo.class);
        ExecutionEventRepo eventRepo = mock(ExecutionEventRepo.class);
        ExecutionRunSkillRepo skillRepo = mock(ExecutionRunSkillRepo.class);
        when(runRepo.lockByRunId("run-skill")).thenReturn(Optional.of(new ExecutionRunEntity()));
        ExecutionEventEntity existing = new ExecutionEventEntity();
        existing.setRunId("run-skill");
        existing.setSequenceNo(2L);
        existing.setEventType(ExecutionEventType.SKILL_ACTIVATED);
        when(eventRepo.findBySequence("run-skill", 2L)).thenReturn(Optional.of(existing));
        when(skillRepo.existsByEvent("run-skill", 2L)).thenReturn(true);
        ExecutionPersistenceServiceImpl service = new ExecutionPersistenceServiceImpl(runRepo,
                mock(ExecutionTaskSnapshotRepo.class), mock(ExecutionStepAttemptRepo.class), eventRepo,
                skillRepo, new ExecutionTaskChecksum());

        ExecutionEventEntity result = service.appendSkillActivationEvent(ExecutionEventCommand.builder()
                        .runId("run-skill").sequenceNo(2L)
                        .eventType(ExecutionEventType.SKILL_ACTIVATED).message("Skill已激活").build(),
                ExecutionSkillActivationCommand.builder().runId("run-skill").agentVersionId(20L)
                        .skillId(1L).skillVersionId(11L).skillCode("fact_check").checksum("checksum")
                        .reason(SkillActivationReason.EXPLICIT).effectiveToolCodes(List.of("search")).build());

        assertEquals(existing, result);
        verify(skillRepo, org.mockito.Mockito.never()).insert(org.mockito.ArgumentMatchers.any());
    }

    private static ExecutionPersistenceServiceImpl service(ExecutionRunRepo runRepo, ExecutionEventRepo eventRepo) {
        return new ExecutionPersistenceServiceImpl(runRepo, mock(ExecutionTaskSnapshotRepo.class),
                mock(ExecutionStepAttemptRepo.class), eventRepo, mock(ExecutionRunSkillRepo.class),
                new ExecutionTaskChecksum());
    }

    private static AgentRuntimeSnapshot supervisor() {
        return new AgentRuntimeSnapshot(40L, 41L, AgentRuntimeDefinition.builder()
                .schemaVersion(com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION)
                .role(AgentRole.SUPERVISOR)
                .chatModelInstanceId(31L)
                .systemPromptVersionId(32L)
                .toolCodes(java.util.List.of())
                .skills(java.util.List.of())
                .planPolicy(PlanPolicy.defaults())
                .build());
    }
}
