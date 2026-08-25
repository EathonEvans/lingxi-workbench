package com.lingxi.workbench.application.runtime.service.interaction;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeApprovedInvocation;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpointStore;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeInterruptionRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.execution.RedisLockExecutor;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Objects;

/** AgentScope 状态、人工交互和精确 Tool 调用意图的 PostgreSQL 实现。 */
@Service
@RequiredArgsConstructor
public class ApplicationRuntimeCheckpointStore implements AgentScopeCheckpointStore {

    private static final Duration CLAIM_LOCK_LEASE = Duration.ofSeconds(15);

    private final InteractionSessionRepo interactionRepo;
    private final ExecutionCheckpointRepo checkpointRepo;
    private final CapabilityInvocationRepo invocationRepo;
    private final ExecutionPersistenceService executionPersistenceService;
    private final RedisLockExecutor redisLockExecutor;

    @Override
    @Transactional
    public RuntimeInterruption interrupt(AgentScopeInterruptionRequest request) {
        AgentScopeCheckpoint checkpoint = Objects.requireNonNull(request.checkpoint(), "AgentScope检查点不能为空");
        requireCheckpointContext(checkpoint);
        AgentToolDefinition definition = Objects.requireNonNull(request.definition(), "Tool定义不能为空");
        requireCurrentVersion(checkpoint);
        InteractionSessionEntity pending = interactionRepo.findAnyPending(
                checkpoint.getConversationId(), checkpoint.getUserId(), checkpoint.getApplicationVersionId()).orElse(null);
        if (pending != null) {
            if (pending.getInteractionType() != InteractionSessionType.ACTION_APPROVAL
                    || !Objects.equals(pending.getApprovalFingerprint(), checkpoint.getFingerprint())) {
                throw conflict("当前运行已有待处理人工交互");
            }
            return toInterruption(pending);
        }

        ExecutionCheckpointEntity entity = new ExecutionCheckpointEntity();
        entity.setRunId(checkpoint.getRunId());
        entity.setAssistantMessageId(checkpoint.getAssistantMessageId());
        entity.setCheckpointType(ExecutionCheckpointType.TOOL_APPROVAL);
        entity.setStatus(ExecutionCheckpointStatus.ACTIVE);
        entity.setCheckpointVersion(AgentScopeCheckpoint.CURRENT_VERSION);
        entity.setPayload(checkpoint);
        entity.setChecksum(checkpoint.getFingerprint());
        entity.setExpiresAt(checkpoint.getExpiresAt());
        checkpointRepo.insert(entity);

        CapabilityInvocationEntity invocation = new CapabilityInvocationEntity();
        invocation.setRunId(checkpoint.getRunId());
        invocation.setCheckpointId(entity.getId());
        invocation.setAssistantMessageId(checkpoint.getAssistantMessageId());
        invocation.setToolCode(definition.getCode());
        invocation.setToolInput(new JSONObject(checkpoint.getPendingToolCall().input()));
        invocation.setResourceScope(checkpoint.getResourceScope());
        invocation.setApprovalFingerprint(checkpoint.getFingerprint());
        invocation.setStatus(CapabilityInvocationStatus.PENDING_APPROVAL);
        invocation.setToolConfigurationHash(definition.getConfigurationHash());
        invocationRepo.insert(invocation);

        InteractionSessionContext interactionContext = InteractionSessionContext.builder()
                .originalQuery(checkpoint.getQuery())
                .title("确认执行“" + definition.getDisplayName() + "”")
                .summary("该操作需要你的明确批准，批准前不会调用外部工具。")
                .riskLevel(definition.getSecurityPolicy().getRiskLevel().name())
                .resourceScope(checkpoint.getResourceScope())
                .toolCode(definition.getCode())
                .build();
        InteractionSessionEntity interaction = new InteractionSessionEntity();
        interaction.setConversationId(checkpoint.getConversationId());
        interaction.setUserId(checkpoint.getUserId());
        interaction.setApplicationVersionId(checkpoint.getApplicationVersionId());
        interaction.setAssistantMessageId(checkpoint.getAssistantMessageId());
        interaction.setRunId(checkpoint.getRunId());
        interaction.setCheckpointId(entity.getId());
        interaction.setResumeCapabilityCode(checkpoint.getCapabilityCode());
        interaction.setOrchestratorAgentId(checkpoint.getAgentId());
        interaction.setOrchestratorAgentVersionId(checkpoint.getAgentVersionId());
        interaction.setInteractionType(InteractionSessionType.ACTION_APPROVAL);
        interaction.setStatus(InteractionSessionStatus.PENDING);
        interaction.setRoundNo(1);
        interaction.setRevision(0);
        interaction.setApprovalFingerprint(checkpoint.getFingerprint());
        interaction.setContext(interactionContext);
        interaction.setExpiresAt(checkpoint.getExpiresAt());
        interactionRepo.insert(interaction);
        if (!invocationRepo.bindInteraction(invocation.getId(), interaction.getId())) {
            throw conflict("Tool调用意图绑定审批交互失败");
        }
        executionPersistenceService.changeRunStatus(checkpoint.getRunId(),
                ExecutionRunStatus.RUNNING, ExecutionRunStatus.WAITING_HUMAN, "TOOL_APPROVAL");
        return toInterruption(interaction);
    }

    @Override
    @Transactional
    public AgentScopeApprovedInvocation claimApproved(AgentScopeCheckpoint checkpoint, Long interactionId, String fingerprint) {
        requireCurrentVersion(checkpoint);
        return redisLockExecutor.tryExecute("lingxi:runtime:run:" + checkpoint.getRunId(),
                        Duration.ZERO, CLAIM_LOCK_LEASE,
                        () -> claimApprovedInLock(checkpoint, interactionId, fingerprint))
                .orElseThrow(() -> conflict("当前运行正在由其他实例恢复"));
    }

    /** Redis 锁仅覆盖审批领取和数据库 CAS，不覆盖后续模型或外部 Tool 调用。 */
    private AgentScopeApprovedInvocation claimApprovedInLock(AgentScopeCheckpoint checkpoint, Long interactionId, String fingerprint) {
        InteractionSessionEntity interaction = interactionRepo.findOwned(
                        interactionId, checkpoint.getConversationId(), checkpoint.getUserId())
                .orElseThrow(() -> conflict("HITL审批不存在"));
        ExecutionCheckpointEntity stored = checkpointRepo.findById(interaction.getCheckpointId())
                .orElseThrow(() -> conflict("HITL检查点不存在"));
        CapabilityInvocationEntity invocation = invocationRepo.findByInteractionId(interaction.getId())
                .orElseThrow(() -> conflict("Tool调用意图不存在"));
        requireRestorable(interaction, stored, invocation, checkpoint, fingerprint);
        if (!invocationRepo.changeStatus(invocation.getId(), CapabilityInvocationStatus.APPROVED,
                CapabilityInvocationStatus.RUNNING, invocation.getIdempotencyKey(), null, null)) {
            throw conflict("Tool调用已经被其他恢复任务领取");
        }
        return new AgentScopeApprovedInvocation(invocation.getId(), invocation.getIdempotencyKey(),
                checkpoint.getPendingToolCall());
    }

    @Override
    @Transactional
    public void complete(Long invocationId, String result) {
        CapabilityInvocationEntity invocation = requireInvocation(invocationId);
        String digest = cn.hutool.crypto.digest.DigestUtil.sha256Hex(Objects.requireNonNullElse(result, ""));
        if (!invocationRepo.changeStatus(invocationId, CapabilityInvocationStatus.RUNNING,
                CapabilityInvocationStatus.SUCCEEDED, invocation.getIdempotencyKey(), digest, null)
                || !checkpointRepo.consume(invocation.getCheckpointId(), OffsetDateTime.now())
                || !interactionRepo.complete(invocation.getInteractionId())) {
            throw conflict("HITL Tool调用终态确认失败");
        }
    }

    @Override
    @Transactional
    public void fail(Long invocationId, RuntimeException exception) {
        if (!invocationRepo.changeStatus(invocationId, CapabilityInvocationStatus.RUNNING,
                CapabilityInvocationStatus.FAILED, null, null, exception.getClass().getSimpleName())) {
            throw conflict("Tool失败终态已经变化");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReconciliationPending(Long invocationId, RuntimeException exception) {
        CapabilityInvocationEntity invocation = requireInvocation(invocationId);
        if (!invocationRepo.changeStatus(invocationId, CapabilityInvocationStatus.RUNNING,
                CapabilityInvocationStatus.RECONCILIATION_PENDING, invocation.getIdempotencyKey(), null,
                exception.getClass().getSimpleName())) {
            throw conflict("Tool调用无法进入待对账状态");
        }
        executionPersistenceService.changeRunStatus(invocation.getRunId(), ExecutionRunStatus.RUNNING,
                ExecutionRunStatus.RECONCILIATION_PENDING, "TOOL_RECONCILIATION");
    }

    private static void requireRestorable(InteractionSessionEntity interaction,
                                          ExecutionCheckpointEntity stored,
                                          CapabilityInvocationEntity invocation,
                                          AgentScopeCheckpoint checkpoint, String fingerprint) {
        AgentScopeCheckpoint storedCheckpoint = stored.getPayload();
        if (storedCheckpoint != null) {
            requireCurrentVersion(storedCheckpoint);
        }
        if (interaction.getStatus() != InteractionSessionStatus.RESOLVED
                || stored.getStatus() != ExecutionCheckpointStatus.ACTIVE
                || stored.getCheckpointVersion() == null
                || stored.getCheckpointVersion() != AgentScopeCheckpoint.CURRENT_VERSION
                || storedCheckpoint == null
                || !Objects.equals(storedCheckpoint.getVersion(), AgentScopeCheckpoint.CURRENT_VERSION)
                || invocation.getStatus() != CapabilityInvocationStatus.APPROVED
                || interaction.getExpiresAt().isBefore(OffsetDateTime.now())
                || stored.getExpiresAt().isBefore(OffsetDateTime.now())
                || !Objects.equals(interaction.getRunId(), checkpoint.getRunId())
                || !Objects.equals(interaction.getApplicationVersionId(), checkpoint.getApplicationVersionId())
                || !Objects.equals(interaction.getAssistantMessageId(), checkpoint.getAssistantMessageId())
                || !Objects.equals(stored.getRunId(), checkpoint.getRunId())
                || !Objects.equals(stored.getChecksum(), fingerprint)
                || !Objects.equals(storedCheckpoint.getFingerprint(), fingerprint)
                || !Objects.equals(storedCheckpoint.getRunId(), checkpoint.getRunId())
                || !Objects.equals(storedCheckpoint.getPendingToolCall().name(),
                        checkpoint.getPendingToolCall().name())
                || !Objects.equals(storedCheckpoint.getPendingToolCall().input(),
                        checkpoint.getPendingToolCall().input())
                || !Objects.equals(checkpoint.getFingerprint(), fingerprint)
                || !Objects.equals(invocation.getApprovalFingerprint(), fingerprint)
                || !Objects.equals(invocation.getToolCode(), checkpoint.getPendingToolCall().name())
                || !Objects.equals(invocation.getToolInput(), new JSONObject(
                        checkpoint.getPendingToolCall().input()))) {
            throw conflict("HITL审批已失效、已漂移或不属于当前运行");
        }
    }

    private static void requireCurrentVersion(AgentScopeCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getVersion() == null
                || checkpoint.getVersion() != AgentScopeCheckpoint.CURRENT_VERSION
                || checkpoint.getRunId() == null
                || checkpoint.getAgentStateJson() == null || checkpoint.getAgentStateJson().isBlank()
                || checkpoint.getPendingToolCall() == null || checkpoint.getFingerprint() == null) {
            throw conflict("AgentScope Checkpoint版本或内容不合法");
        }
    }

    private CapabilityInvocationEntity requireInvocation(Long invocationId) {
        return invocationRepo.findById(Objects.requireNonNull(invocationId, "Tool调用主键不能为空"))
                .orElseThrow(() -> conflict("Tool调用意图不存在"));
    }

    private static void requireCheckpointContext(AgentScopeCheckpoint checkpoint) {
        if (checkpoint.getConversationId() == null || checkpoint.getUserId() == null
                || checkpoint.getApplicationVersionId() == null || checkpoint.getAssistantMessageId() == null
                || checkpoint.getQuery() == null || checkpoint.getQuery().isBlank()
                || checkpoint.getCapabilityCode() == null || checkpoint.getCapabilityCode().isBlank()) {
            throw conflict("检查点上下文不能为空");
        }
    }

    private static RuntimeInterruption toInterruption(InteractionSessionEntity interaction) {
        InteractionSessionContext context = interaction.getContext();
        return RuntimeInterruption.builder()
                .interactionId(interaction.getId())
                .checkpointId(interaction.getCheckpointId())
                .fingerprint(interaction.getApprovalFingerprint())
                .title(context.getTitle())
                .summary(context.getSummary())
                .riskLevel(context.getRiskLevel())
                .resourceScope(context.getResourceScope())
                .revision(interaction.getRevision())
                .expiresAt(interaction.getExpiresAt())
                .build();
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}
