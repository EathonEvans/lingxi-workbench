package com.lingxi.workbench.application.runtime.service.interaction;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionCheckpointPayload;
import com.lingxi.workbench.application.runtime.model.HitlApprovalRef;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.application.runtime.model.ToolApprovalAuthorization;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.chat.model.bo.ChatInteractionEventBO;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolApprovalRequest;
import com.lingxi.workbench.infra.ai.tool.model.ToolSecurityPolicy;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Tool 审批创建、恢复认领和执行终态的 PostgreSQL 事务边界。 */
@Service
@RequiredArgsConstructor
public class ToolApprovalTxService {

    private final InteractionSessionRepo interactionRepo;
    private final ExecutionCheckpointRepo checkpointRepo;
    private final CapabilityInvocationRepo invocationRepo;
    private final ExecutionPersistenceService executionPersistenceService;
    private final ToolApprovalFingerprint fingerprint;

    @Transactional
    public ToolApprovalAuthorization authorize(AgentToolApprovalRequest request, ChatExecutionContext execution,
                                               AgentRuntimeSnapshot agent) {
        ToolSecurityPolicy policy = request.getDefinition().getSecurityPolicy();
        if (!policy.isApprovalRequired()) {
            return ToolApprovalAuthorization.unrestricted();
        }
        HitlApprovalRef approvalRef = execution.getApprovalRef();
        if (approvalRef != null) {
            return claimApproved(request, execution, approvalRef);
        }
        return open(request, execution, agent, policy);
    }

    @Transactional
    public void complete(Long invocationId, String result) {
        if (invocationId == null) {
            return;
        }
        CapabilityInvocationEntity invocation = requireInvocation(invocationId);
        String digest = cn.hutool.crypto.digest.DigestUtil.sha256Hex(Objects.requireNonNullElse(result, ""));
        if (!invocationRepo.changeStatus(invocationId, CapabilityInvocationStatus.RUNNING,
                CapabilityInvocationStatus.SUCCEEDED, invocation.getIdempotencyKey(), digest, null)) {
            throw conflict("Tool调用终态已变化");
        }
        if (!checkpointRepo.consume(invocation.getCheckpointId(), OffsetDateTime.now())) {
            throw conflict("HITL检查点终态已变化");
        }
        if (!interactionRepo.complete(invocation.getInteractionId())) {
            throw conflict("HITL交互终态已变化");
        }
    }

    @Transactional
    public void fail(Long invocationId, RuntimeException exception) {
        if (invocationId == null) {
            return;
        }
        invocationRepo.changeStatus(invocationId, CapabilityInvocationStatus.RUNNING,
                CapabilityInvocationStatus.FAILED, null, null, exception.getClass().getSimpleName());
    }

    private ToolApprovalAuthorization claimApproved(AgentToolApprovalRequest request,
                                                     ChatExecutionContext execution,
                                                     HitlApprovalRef approvalRef) {
        InteractionSessionEntity interaction = interactionRepo.findOwned(
                        approvalRef.interactionId(), request.getExecutionContext().getUserId())
                .orElseThrow(() -> conflict("HITL审批不存在"));
        ExecutionCheckpointEntity checkpoint = checkpointRepo.findById(interaction.getCheckpointId())
                .orElseThrow(() -> conflict("HITL检查点不存在"));
        CapabilityInvocationEntity invocation = invocationRepo.findByInteractionId(interaction.getId())
                .orElseThrow(() -> conflict("Tool调用意图不存在"));
        requireRestorable(interaction, checkpoint, invocation, execution);
        String currentFingerprint = fingerprint.approval(
                execution.getExecutionId(), request.getDefinition().getCode(),
                fingerprint.configHash(request.getDefinition()), request.getToolInput(),
                request.getDefinition().getSecurityPolicy().getResourceScope(), interaction.getExpiresAt());
        if (!Objects.equals(currentFingerprint, approvalRef.fingerprint())
                || !Objects.equals(currentFingerprint, interaction.getApprovalFingerprint())
                || !Objects.equals(currentFingerprint, checkpoint.getChecksum())
                || !Objects.equals(currentFingerprint, invocation.getApprovalFingerprint())) {
            throw conflict("Tool参数、资源范围或配置已变化，原审批不可使用");
        }
        if (!invocationRepo.changeStatus(invocation.getId(), CapabilityInvocationStatus.APPROVED,
                CapabilityInvocationStatus.RUNNING, invocation.getIdempotencyKey(), null, null)) {
            throw conflict("Tool调用已经被其他恢复任务领取");
        }
        return ToolApprovalAuthorization.permitted(invocation.getId());
    }

    private ToolApprovalAuthorization open(AgentToolApprovalRequest request, ChatExecutionContext execution,
                                           AgentRuntimeSnapshot agent, ToolSecurityPolicy policy) {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(policy.getApprovalTtlSeconds());
        String configHash = fingerprint.configHash(request.getDefinition());
        String approvalFingerprint = fingerprint.approval(execution.getExecutionId(),
                request.getDefinition().getCode(), configHash, request.getToolInput(),
                policy.getResourceScope(), expiresAt);
        InteractionSessionEntity pending = interactionRepo.findAnyPending(
                request.getExecutionContext().getConversationId(), request.getExecutionContext().getUserId(),
                execution.getConversationContext().getApplicationSnapshot().versionId()).orElse(null);
        if (pending != null) {
            if (pending.getInteractionType() != InteractionSessionType.ACTION_APPROVAL
                    || !Objects.equals(pending.getApprovalFingerprint(), approvalFingerprint)) {
                throw conflict("当前运行已有待处理人工交互");
            }
            CapabilityInvocationEntity invocation = invocationRepo.findByInteractionId(pending.getId())
                    .orElseThrow(() -> conflict("待审批Tool调用不存在"));
            return ToolApprovalAuthorization.pending(invocation.getId(), toEvent(pending));
        }

        String capabilityCode = resolveCapabilityCode(execution, agent);
        ExecutionCheckpointPayload payload = ExecutionCheckpointPayload.builder()
                .query(execution.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY))
                .capabilityCode(capabilityCode)
                .agentId(agent.agentId())
                .agentVersionId(agent.agentVersionId())
                .toolCode(request.getDefinition().getCode())
                .toolConfigHash(configHash)
                .toolInput(request.getToolInput())
                .resourceScope(policy.getResourceScope())
                .build();
        ExecutionCheckpointEntity checkpoint = new ExecutionCheckpointEntity();
        checkpoint.setRunId(execution.getExecutionId());
        checkpoint.setAssistantMessageId(request.getExecutionContext().getAssistantMessageId());
        checkpoint.setCheckpointType(ExecutionCheckpointType.TOOL_APPROVAL);
        checkpoint.setStatus(ExecutionCheckpointStatus.ACTIVE);
        checkpoint.setCheckpointVersion(1);
        checkpoint.setPayload(payload);
        checkpoint.setChecksum(approvalFingerprint);
        checkpoint.setExpiresAt(expiresAt);
        checkpointRepo.insert(checkpoint);

        CapabilityInvocationEntity invocation = new CapabilityInvocationEntity();
        invocation.setRunId(execution.getExecutionId());
        invocation.setCheckpointId(checkpoint.getId());
        invocation.setAssistantMessageId(request.getExecutionContext().getAssistantMessageId());
        invocation.setToolCode(request.getDefinition().getCode());
        invocation.setToolInput(requireObject(request.getToolInput()));
        invocation.setResourceScope(policy.getResourceScope());
        invocation.setApprovalFingerprint(approvalFingerprint);
        invocation.setStatus(CapabilityInvocationStatus.PENDING_APPROVAL);
        invocationRepo.insert(invocation);

        InteractionSessionContext context = InteractionSessionContext.builder()
                .originalQuery(payload.getQuery())
                .title("确认执行“" + request.getDefinition().getDisplayName() + "”")
                .summary("该操作需要你的明确批准，批准前不会调用外部工具。")
                .riskLevel(policy.getRiskLevel().name())
                .resourceScope(policy.getResourceScope())
                .toolCode(request.getDefinition().getCode())
                .build();
        InteractionSessionEntity interaction = new InteractionSessionEntity();
        interaction.setConversationId(request.getExecutionContext().getConversationId());
        interaction.setUserId(request.getExecutionContext().getUserId());
        interaction.setApplicationVersionId(execution.getConversationContext().getApplicationSnapshot().versionId());
        interaction.setAssistantMessageId(request.getExecutionContext().getAssistantMessageId());
        interaction.setRunId(execution.getExecutionId());
        interaction.setCheckpointId(checkpoint.getId());
        interaction.setResumeCapabilityCode(capabilityCode);
        interaction.setOrchestratorAgentId(agent.agentId());
        interaction.setOrchestratorAgentVersionId(agent.agentVersionId());
        interaction.setInteractionType(InteractionSessionType.ACTION_APPROVAL);
        interaction.setStatus(InteractionSessionStatus.PENDING);
        interaction.setRoundNo(1);
        interaction.setRevision(0);
        interaction.setApprovalFingerprint(approvalFingerprint);
        interaction.setContext(context);
        interaction.setExpiresAt(expiresAt);
        interactionRepo.insert(interaction);
        if (!invocationRepo.bindInteraction(invocation.getId(), interaction.getId())) {
            throw conflict("Tool调用意图绑定审批交互失败");
        }
        executionPersistenceService.changeRunStatus(execution.getExecutionId(),
                ExecutionRunStatus.RUNNING, ExecutionRunStatus.WAITING_HUMAN, "TOOL_APPROVAL");
        return ToolApprovalAuthorization.pending(invocation.getId(), toEvent(interaction));
    }

    private static void requireRestorable(InteractionSessionEntity interaction,
                                          ExecutionCheckpointEntity checkpoint,
                                          CapabilityInvocationEntity invocation,
                                          ChatExecutionContext execution) {
        if (interaction.getStatus() != InteractionSessionStatus.RESOLVED
                || checkpoint.getStatus() != ExecutionCheckpointStatus.ACTIVE
                || invocation.getStatus() != CapabilityInvocationStatus.APPROVED
                || interaction.getExpiresAt().isBefore(OffsetDateTime.now())
                || checkpoint.getExpiresAt().isBefore(OffsetDateTime.now())
                || !Objects.equals(interaction.getRunId(), execution.getExecutionId())
                || !Objects.equals(interaction.getAssistantMessageId(), execution.getTurn().assistantMessageId())) {
            throw conflict("HITL审批已失效或不属于当前运行");
        }
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

    private static String resolveCapabilityCode(ChatExecutionContext execution, AgentRuntimeSnapshot agent) {
        return execution.getConversationContext().getApplicationSnapshot().definition().capabilityCatalog()
                .capabilities().stream()
                .filter(capability -> capability.getType() == ApplicationCapabilityType.AGENT)
                .filter(capability -> Objects.equals(capability.getTargetId(), agent.agentId()))
                .filter(capability -> Objects.equals(capability.getTargetVersionId(), agent.agentVersionId()))
                .map(ApplicationCapabilityDefinition::getCode)
                .findFirst()
                .orElseThrow(() -> conflict("当前Agent不在Application能力目录中"));
    }

    private CapabilityInvocationEntity requireInvocation(Long invocationId) {
        return invocationRepo.findById(invocationId)
                .orElseThrow(() -> conflict("Tool调用意图不存在"));
    }

    private static ChatInteractionEventBO toEvent(InteractionSessionEntity interaction) {
        InteractionSessionContext context = interaction.getContext();
        return ChatInteractionEventBO.builder()
                .interactionId(String.valueOf(interaction.getId()))
                .interactionType(interaction.getInteractionType().getCode())
                .title(context.getTitle())
                .summary(context.getSummary())
                .riskLevel(context.getRiskLevel())
                .resourceScope(context.getResourceScope())
                .fingerprint(interaction.getApprovalFingerprint())
                .revision(interaction.getRevision())
                .expiresAt(interaction.getExpiresAt())
                .build();
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }
}
