package com.lingxi.workbench.application.runtime.service.impl;

import cn.hutool.core.util.IdUtil;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionPlanSnapshotEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionRunEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;
import com.lingxi.workbench.application.runtime.repo.ExecutionEventRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionPlanSnapshotRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionStepAttemptRepo;
import com.lingxi.workbench.application.runtime.service.ExecutionPersistenceService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** Plan-and-Execute运行数据的事务实现。 */
@Service
@RequiredArgsConstructor
public class ExecutionPersistenceServiceImpl implements ExecutionPersistenceService {

    private final ExecutionRunRepo runRepo;
    private final ExecutionPlanSnapshotRepo planRepo;
    private final ExecutionStepAttemptRepo stepAttemptRepo;
    private final ExecutionEventRepo eventRepo;
    private final ExecutionDecisionChecksum checksum;

    @Override
    @Transactional
    public String startRun(ExecutionRunStartCommand command) {
        requireStartCommand(command);
        ApplicationRuntimeSnapshot application = command.getApplication();
        ChatTurnBO turn = command.getTurn();
        PlannerPolicy policy = command.getPlannerPolicy();
        String runId = IdUtil.getSnowflakeNextIdStr();
        ExecutionRunEntity run = new ExecutionRunEntity();
        run.setRunId(runId);
        run.setAssistantMessageId(turn.assistantMessageId());
        run.setConversationId(turn.conversationId());
        run.setUserId(command.getUserId());
        run.setApplicationId(application.applicationId());
        run.setApplicationVersionId(application.applicationVersionId());
        run.setPlannerModelInstanceId(policy.getChatModelInstanceId());
        run.setPlannerPromptVersionId(policy.getPromptVersionId());
        runRepo.insert(run);
        return runId;
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
        snapshot.setDecisionAction(command.decision().getAction());
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
    public ExecutionEventEntity appendEvent(ExecutionEventCommand command) {
        if (command == null || command.getEventType() == null) {
            throw invalid("执行事件参数不完整");
        }
        lockRun(command.getRunId());
        ExecutionEventEntity event = new ExecutionEventEntity();
        event.setRunId(command.getRunId());
        event.setSequenceNo(eventRepo.nextSequence(command.getRunId()));
        event.setEventType(command.getEventType());
        event.setPlanNo(command.getPlanNo());
        event.setStepId(command.getStepId());
        event.setDisplayName(command.getDisplayName());
        event.setStepStatus(command.getStepStatus());
        event.setMessage(normalizeMessage(command.getMessage()));
        event.setOccurredAt(OffsetDateTime.now());
        eventRepo.insert(event);
        return event;
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
                || command.getUserId() == null || command.getPlannerPolicy() == null
                || command.getPlannerPolicy().getChatModelInstanceId() == null
                || command.getPlannerPolicy().getPromptVersionId() == null) {
            throw invalid("Plan-and-Execute运行元数据不完整");
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
        runRepo.lockByRunId(runId).orElseThrow(() -> invalid("Plan-and-Execute运行不存在"));
    }

    private void requireRun(String runId) {
        if (runId == null || runId.isBlank() || runRepo.findByRunId(runId).isEmpty()) {
            throw invalid("Plan-and-Execute运行不存在");
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
