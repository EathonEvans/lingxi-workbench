package com.lingxi.workbench.application.runtime.service.interaction;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.agentruntime.checkpoint.HumanApprovalRequest;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointContext;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeCheckpointStore;
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
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Runtime Checkpoint、人工交互和精确Tool调用意图的PostgreSQL实现。 */
@Service
@RequiredArgsConstructor
public class ApplicationRuntimeCheckpointStore implements RuntimeCheckpointStore {

    private final InteractionSessionRepo interactionRepo;
    private final ExecutionCheckpointRepo checkpointRepo;
    private final CapabilityInvocationRepo invocationRepo;
    private final ExecutionPersistenceService executionPersistenceService;

    @Override
    @Transactional
    public RuntimeInterruption interrupt(HumanApprovalRequest request) {
        RuntimeCheckpoint checkpoint = Objects.requireNonNull(request.checkpoint(), "Runtime检查点不能为空");
        RuntimeCheckpointContext context = Objects.requireNonNull(checkpoint.getContext(), "检查点上下文不能为空");
        AgentToolDefinition definition = Objects.requireNonNull(request.definition(), "Tool定义不能为空");
        requireCurrentVersion(checkpoint);
        InteractionSessionEntity pending = interactionRepo.findAnyPending(
                context.getConversationId(), context.getUserId(), context.getApplicationVersionId()).orElse(null);
        if (pending != null) {
            if (pending.getInteractionType() != InteractionSessionType.ACTION_APPROVAL
                    || !Objects.equals(pending.getApprovalFingerprint(), checkpoint.getFingerprint())) {
                throw conflict("当前运行已有待处理人工交互");
            }
            return toInterruption(pending);
        }

        ExecutionCheckpointEntity entity = new ExecutionCheckpointEntity();
        entity.setRunId(checkpoint.getState().getRunId());
        entity.setAssistantMessageId(context.getAssistantMessageId());
        entity.setCheckpointType(ExecutionCheckpointType.TOOL_APPROVAL);
        entity.setStatus(ExecutionCheckpointStatus.ACTIVE);
        entity.setCheckpointVersion(RuntimeCheckpoint.CURRENT_VERSION);
        entity.setPayload(checkpoint);
        entity.setChecksum(checkpoint.getFingerprint());
        entity.setExpiresAt(checkpoint.getExpiresAt());
        checkpointRepo.insert(entity);

        CapabilityInvocationEntity invocation = new CapabilityInvocationEntity();
        invocation.setRunId(checkpoint.getState().getRunId());
        invocation.setCheckpointId(entity.getId());
        invocation.setAssistantMessageId(context.getAssistantMessageId());
        invocation.setToolCode(definition.getCode());
        invocation.setToolInput(requireObject(checkpoint.getPendingToolCall().getArguments()));
        invocation.setResourceScope(checkpoint.getResourceScope());
        invocation.setApprovalFingerprint(checkpoint.getFingerprint());
        invocation.setStatus(CapabilityInvocationStatus.PENDING_APPROVAL);
        invocationRepo.insert(invocation);

        InteractionSessionContext interactionContext = InteractionSessionContext.builder()
                .originalQuery(context.getQuery())
                .title("确认执行“" + definition.getDisplayName() + "”")
                .summary("该操作需要你的明确批准，批准前不会调用外部工具。")
                .riskLevel(definition.getSecurityPolicy().getRiskLevel().name())
                .resourceScope(checkpoint.getResourceScope())
                .toolCode(definition.getCode())
                .build();
        InteractionSessionEntity interaction = new InteractionSessionEntity();
        interaction.setConversationId(context.getConversationId());
        interaction.setUserId(context.getUserId());
        interaction.setApplicationVersionId(context.getApplicationVersionId());
        interaction.setAssistantMessageId(context.getAssistantMessageId());
        interaction.setRunId(checkpoint.getState().getRunId());
        interaction.setCheckpointId(entity.getId());
        interaction.setResumeCapabilityCode(context.getCapabilityCode());
        interaction.setOrchestratorAgentId(checkpoint.getState().getAgentId());
        interaction.setOrchestratorAgentVersionId(checkpoint.getState().getAgentVersionId());
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
        executionPersistenceService.changeRunStatus(checkpoint.getState().getRunId(),
                ExecutionRunStatus.RUNNING, ExecutionRunStatus.WAITING_HUMAN, "TOOL_APPROVAL");
        return toInterruption(interaction);
    }

    @Override
    @Transactional
    public Long claimApproved(RuntimeCheckpoint checkpoint, Long interactionId, String fingerprint) {
        requireCurrentVersion(checkpoint);
        RuntimeCheckpointContext context = checkpoint.getContext();
        InteractionSessionEntity interaction = interactionRepo.findOwned(
                        interactionId, context.getConversationId(), context.getUserId())
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
        return invocation.getId();
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
                                          RuntimeCheckpoint checkpoint, String fingerprint) {
        RuntimeCheckpointContext context = checkpoint.getContext();
        RuntimeCheckpoint storedCheckpoint = stored.getPayload();
        if (storedCheckpoint != null) {
            requireCurrentVersion(storedCheckpoint);
        }
        if (interaction.getStatus() != InteractionSessionStatus.RESOLVED
                || stored.getStatus() != ExecutionCheckpointStatus.ACTIVE
                || stored.getCheckpointVersion() == null
                || stored.getCheckpointVersion() != RuntimeCheckpoint.CURRENT_VERSION
                || storedCheckpoint == null
                || !Objects.equals(storedCheckpoint.getVersion(), RuntimeCheckpoint.CURRENT_VERSION)
                || invocation.getStatus() != CapabilityInvocationStatus.APPROVED
                || interaction.getExpiresAt().isBefore(OffsetDateTime.now())
                || stored.getExpiresAt().isBefore(OffsetDateTime.now())
                || !Objects.equals(interaction.getRunId(), checkpoint.getState().getRunId())
                || !Objects.equals(interaction.getApplicationVersionId(), context.getApplicationVersionId())
                || !Objects.equals(interaction.getAssistantMessageId(), context.getAssistantMessageId())
                || !Objects.equals(stored.getRunId(), checkpoint.getState().getRunId())
                || !Objects.equals(stored.getChecksum(), fingerprint)
                || !Objects.equals(storedCheckpoint.getFingerprint(), fingerprint)
                || !Objects.equals(storedCheckpoint.getState().getRunId(), checkpoint.getState().getRunId())
                || !Objects.equals(storedCheckpoint.getPendingToolCall().getName(),
                        checkpoint.getPendingToolCall().getName())
                || !Objects.equals(storedCheckpoint.getPendingToolCall().getArguments(),
                        checkpoint.getPendingToolCall().getArguments())
                || !Objects.equals(checkpoint.getFingerprint(), fingerprint)
                || !Objects.equals(invocation.getApprovalFingerprint(), fingerprint)
                || !Objects.equals(invocation.getToolCode(), checkpoint.getPendingToolCall().getName())
                || !Objects.equals(invocation.getToolInput(), requireObject(
                        checkpoint.getPendingToolCall().getArguments()))) {
            throw conflict("HITL审批已失效、已漂移或不属于当前运行");
        }
    }

    private static void requireCurrentVersion(RuntimeCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getVersion() == null
                || checkpoint.getVersion() != RuntimeCheckpoint.CURRENT_VERSION
                || checkpoint.getState() == null || checkpoint.getContext() == null
                || checkpoint.getPendingToolCall() == null || checkpoint.getFingerprint() == null) {
            throw conflict("Runtime Checkpoint版本或内容不合法");
        }
    }

    private CapabilityInvocationEntity requireInvocation(Long invocationId) {
        return invocationRepo.findById(Objects.requireNonNull(invocationId, "Tool调用主键不能为空"))
                .orElseThrow(() -> conflict("Tool调用意图不存在"));
    }

    private static JSONObject requireObject(String toolInput) {
        try {
            JSONObject object = JSON.parseObject(toolInput);
            if (object == null) {
                throw new IllegalArgumentException("Tool参数不能为空");
            }
            return object;
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Tool参数不是有效JSON对象");
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
