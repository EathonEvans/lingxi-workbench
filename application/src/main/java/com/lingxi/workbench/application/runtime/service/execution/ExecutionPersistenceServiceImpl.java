package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.application.runtime.service.plan.ExecutionDecisionChecksum;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.agentruntime.model.SkillActivation;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionPlanSnapshotEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunSkillEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;
import com.lingxi.workbench.application.runtime.repo.ExecutionEventRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionPlanSnapshotRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunSkillRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionStepAttemptRepo;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Application 顶级运行与 Plan 明细的事务实现。 */
@Service
@RequiredArgsConstructor
public class ExecutionPersistenceServiceImpl implements ExecutionPersistenceService {

    private final ExecutionRunRepo runRepo;
    private final ExecutionPlanSnapshotRepo planRepo;
    private final ExecutionStepAttemptRepo stepAttemptRepo;
    private final ExecutionEventRepo eventRepo;
    private final ExecutionRunSkillRepo runSkillRepo;
    private final ExecutionDecisionChecksum checksum;

    @Override
    @Transactional
    public void startRun(ExecutionRunStartCommand command) {
        requireStartCommand(command);
        AppRuntimeSnapshot application = command.getApplication();
        ChatTurnBO turn = command.getTurn();
        AgentRuntimeSnapshot orchestrator = command.getOrchestratorAgent();
        ExecutionRunEntity run = new ExecutionRunEntity();
        run.setRunId(command.getRunId());
        run.setAssistantMessageId(turn.assistantMessageId());
        run.setConversationId(turn.conversationId());
        run.setUserId(command.getUserId());
        run.setApplicationId(application.applicationId());
        run.setApplicationVersionId(application.versionId());
        if (orchestrator != null) {
            run.setOrchestratorAgentId(orchestrator.agentId());
            run.setOrchestratorAgentVersionId(orchestrator.agentVersionId());
            run.setPlannerModelInstanceId(orchestrator.definition().getChatModelInstanceId());
            run.setPlannerPromptVersionId(orchestrator.definition().getSystemPromptVersionId());
        }
        run.setCapabilityCode(command.getCapabilityCode());
        run.setExecutionMode(command.getExecutionMode());
        run.setStatus(ExecutionRunStatus.RUNNING);
        run.setCurrentStage("EXECUTION");
        run.setRevision(0);
        runRepo.insert(run);
    }

    @Override
    @Transactional
    public void changeRunStatus(String runId, ExecutionRunStatus expected, ExecutionRunStatus target,
                                String currentStage) {
        OffsetDateTime completedAt = switch (target) {
            case SUCCEEDED, PARTIAL, FAILED, CANCELLED, EXPIRED -> OffsetDateTime.now();
            default -> null;
        };
        if (!runRepo.changeStatus(runId, expected, target, currentStage, completedAt)) {
            throw invalid("Application运行状态已变化，无法从" + expected + "推进到" + target);
        }
    }

    @Override
    @Transactional
    public void appendPlan(ExecutionPlanSnapshotCommand command) {
        if (command == null || command.decision() == null || command.planNo() == null || command.planNo() < 1) {
            throw invalid("Planner决策快照参数不完整");
        }
        lockRun(command.runId());
        ExecutionPlanSnapshotEntity snapshot = new ExecutionPlanSnapshotEntity();
        snapshot.setRunId(command.runId());
        snapshot.setPlanNo(command.planNo());
        snapshot.setDecisionAction(command.decision().getAction().name());
        snapshot.setCapabilityCode(command.decision().getCapabilityCode());
        snapshot.setDecisionSnapshot(command.decision());
        snapshot.setSnapshotChecksum(checksum.calculate(command.decision()));
        planRepo.insert(snapshot);
    }

    @Override
    @Transactional
    public void appendStepAttempt(ExecutionStepAttemptCommand command) {
        requireStepAttempt(command);
        lockRun(command.getRunId());
        ExecutionStepAttemptEntity attempt = new ExecutionStepAttemptEntity();
        attempt.setRunId(command.getRunId());
        attempt.setPlanNo(command.getPlanNo());
        attempt.setStepId(command.getStep().getId());
        attempt.setAttemptNo(command.getAttemptNo());
        attempt.setCapabilityCode(command.getStep().getCapabilityCode());
        attempt.setStatus(command.getStatus());
        attempt.setStepSnapshot(command.getStep());
        attempt.setResultContent(command.getResultContent());
        attempt.setErrorCode(command.getErrorCode());
        attempt.setStartedAt(command.getStartedAt());
        attempt.setFinishedAt(OffsetDateTime.now());
        stepAttemptRepo.insert(attempt);
    }

    @Override
    @Transactional
    public void recordActivatedSkills(String runId, Long agentVersionId, List<SkillActivation> skills) {
        if (skills.isEmpty()) {
            return;
        }
        requireRun(runId);
        if (agentVersionId == null) {
            throw invalid("记录Skill激活事实时缺少AgentVersion");
        }
        List<ExecutionRunSkillEntity> entities = skills.stream().map(activated -> {
            ExecutionRunSkillEntity entity = new ExecutionRunSkillEntity();
            entity.setRunId(runId);
            entity.setAgentVersionId(agentVersionId);
            entity.setSkillId(activated.binding().getSkillId());
            entity.setSkillVersionId(activated.binding().getSkillVersionId());
            entity.setSkillCode(activated.binding().getSkill().getCode());
            entity.setSkillChecksum(activated.binding().getChecksum());
            entity.setActivationReason(activated.reason());
            entity.setEffectiveToolCodes(activated.binding().getEffectiveToolCodes());
            return entity;
        }).toList();
        runSkillRepo.insertAll(entities);
    }

    @Override
    @Transactional
    public ExecutionEventEntity appendEvent(ExecutionEventCommand command) {
        if (command == null || command.getEventType() == null) {
            throw invalid("执行事件参数不完整");
        }
        lockRun(command.getRunId());
        if (command.getSequenceNo() != null) {
            Optional<ExecutionEventEntity> existing = eventRepo.findBySequence(
                    command.getRunId(), command.getSequenceNo());
            if (existing.isPresent()) {
                if (existing.get().getEventType() != command.getEventType()) {
                    throw invalid("相同执行事件序号对应的事件类型不一致");
                }
                return existing.get();
            }
        }
        ExecutionEventEntity event = new ExecutionEventEntity();
        event.setRunId(command.getRunId());
        long nextSequence = eventRepo.nextSequence(command.getRunId());
        long sequenceNo = command.getSequenceNo() == null ? nextSequence : command.getSequenceNo();
        if (sequenceNo < 1) {
            throw invalid("执行事件序号必须大于0");
        }
        if (sequenceNo != nextSequence) {
            throw invalid("执行事件序号不连续");
        }
        event.setSequenceNo(sequenceNo);
        event.setEventType(command.getEventType());
        event.setPlanNo(command.getPlanNo());
        event.setStepId(command.getStepId());
        event.setDisplayName(command.getDisplayName());
        event.setStepStatus(command.getStepStatus());
        event.setMessage(normalizeMessage(command.getMessage()));
        event.setOccurredAt(command.getOccurredAt() == null
                ? OffsetDateTime.now()
                : OffsetDateTime.ofInstant(command.getOccurredAt(), java.time.ZoneOffset.UTC));
        eventRepo.insert(event);
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public long lastEventSequence(String runId) {
        requireRun(runId);
        return eventRepo.lastSequence(runId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionPlanSnapshotEntity> findPlans(String runId) {
        requireRun(runId);
        return List.copyOf(planRepo.findByRunId(runId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionStepAttemptEntity> findStepAttempts(String runId) {
        requireRun(runId);
        return List.copyOf(stepAttemptRepo.findByRunId(runId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExecutionEventEntity> findEventsAfter(String runId, long afterSequence) {
        requireRun(runId);
        if (afterSequence < 0) {
            throw invalid("执行事件序号不能小于0");
        }
        return List.copyOf(eventRepo.findAfter(runId, afterSequence));
    }

    private static void requireStartCommand(ExecutionRunStartCommand command) {
        if (command == null || command.getApplication() == null || command.getTurn() == null
                || command.getUserId() == null || command.getRunId() == null || command.getRunId().isBlank()
                || command.getCapabilityCode() == null || command.getCapabilityCode().isBlank()
                || command.getExecutionMode() == null || command.getExecutionMode().isBlank()) {
            throw invalid("Application运行元数据不完整");
        }
    }

    private static void requireStepAttempt(ExecutionStepAttemptCommand command) {
        if (command == null || command.getPlanNo() == null || command.getPlanNo() < 1
                || command.getAttemptNo() == null || command.getAttemptNo() < 1
                || command.getStep() == null || command.getStatus() == null || command.getStartedAt() == null) {
            throw invalid("步骤尝试参数不完整");
        }
        if (command.getStatus() == ExecutionStepStatus.RUNNING) {
            throw invalid("步骤尝试只能追加最终状态");
        }
    }

    private void lockRun(String runId) {
        if (runId == null || runId.isBlank()) {
            throw invalid("runId不能为空");
        }
        runRepo.lockByRunId(runId).orElseThrow(() -> invalid("Application运行不存在"));
    }

    private void requireRun(String runId) {
        if (runId == null || runId.isBlank() || runRepo.findByRunId(runId).isEmpty()) {
            throw invalid("Application运行不存在");
        }
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String normalized = message.trim();
        if (normalized.length() > 500) {
            throw invalid("执行事件消息不能超过500个字符");
        }
        return normalized;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}
