package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionDecision;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.HitlDecisionTxBO;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.model.message.HitlResumeMessage;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/** HITL 用户决策的本地事务和 PostgreSQL 回查逻辑。 */
@Service
@RequiredArgsConstructor
public class HitlDecisionTxService {

    private final InteractionSessionRepo interactionRepo;
    private final CapabilityInvocationRepo invocationRepo;
    private final ExecutionCheckpointRepo checkpointRepo;
    private final ExecutionRunRepo runRepo;
    private final ConversationService conversationService;
    private final HitlApprovalClosureTxService closureTxService;
    private final NotificationService notificationService;

    @Transactional
    public void decide(HitlDecisionTxBO tx) {
        HitlResumeMessage event = tx.getEvent();
        InteractionSessionEntity interaction = interactionRepo.findOwned(event.getInteractionId(), tx.getUserId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "HITL交互不存在"));
        requireDecision(interaction, tx);
        if (isSameCommittedDecision(interaction, event.getEventId())) {
            return;
        }
        if (interaction.getExpiresAt().isBefore(OffsetDateTime.now())) {
            expire(interaction);
            throw new BusinessException(HttpStatusEnum.CONFLICT, "审批已过期，请重新发起任务");
        }
        if (tx.getDecision() == InteractionDecision.APPROVE) {
            requireActiveCheckpoint(interaction);
        }
        InteractionSessionStatus target = switch (tx.getDecision()) {
            case APPROVE -> InteractionSessionStatus.RESOLVED;
            case REJECT -> InteractionSessionStatus.REJECTED;
            case CANCEL -> InteractionSessionStatus.CANCELLED;
        };
        boolean decided = interactionRepo.decide(interaction.getId(), tx.getUserId(), tx.getExpectedRevision(),
                target, tx.getDecision(), tx.getUserId(), event.getEventId(), OffsetDateTime.now());
        if (!decided) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "审批状态已变化，请刷新后重试");
        }
        // 决策落库后同步收敛对应站内通知，保持通知中心与交互状态一致。
        notificationService.markInteractionNotificationsRead(tx.getUserId(), interaction.getId());
        CapabilityInvocationEntity invocation = invocationRepo.findByInteractionId(interaction.getId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Tool调用意图不存在"));
        if (tx.getDecision() == InteractionDecision.APPROVE) {
            approve(interaction, invocation, event.getEventId());
            return;
        }
        close(interaction, invocation, event.getEventId(), tx.getDecision());
    }

    /** 用户停止等待态消息时同步收敛 Interaction、Checkpoint、Invocation 与 Run。 */
    @Transactional
    public void cancel(Long assistantMessageId, Long userId) {
        InteractionSessionEntity interaction = interactionRepo.findPendingOwnedByAssistant(
                        assistantMessageId, userId, InteractionSessionType.ACTION_APPROVAL)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "待取消的HITL交互不存在"));
        String eventId = "cancel-" + interaction.getId() + "-" + interaction.getRevision();
        HitlResumeMessage event = HitlResumeMessage.builder()
                .eventId(eventId)
                .interactionId(interaction.getId())
                .conversationId(interaction.getConversationId())
                .assistantMessageId(interaction.getAssistantMessageId())
                .appVersionId(interaction.getApplicationVersionId())
                .build();
        decide(HitlDecisionTxBO.builder()
                .event(event)
                .userId(userId)
                .decision(InteractionDecision.CANCEL)
                .expectedRevision(interaction.getRevision())
                .fingerprint(interaction.getApprovalFingerprint())
                .build());
    }

    /** Broker 回查只读取 PostgreSQL 决策状态，不依赖 Redis 或本机内存。 */
    @Transactional(readOnly = true)
    public Optional<Boolean> transactionState(HitlResumeMessage event) {
        Optional<InteractionSessionEntity> optional = interactionRepo.findById(event.getInteractionId());
        if (optional.isEmpty()) {
            return Optional.of(false);
        }
        InteractionSessionEntity interaction = optional.get();
        if (interaction.getStatus() == InteractionSessionStatus.PENDING) {
            return Optional.empty();
        }
        return Optional.of(isSameCommittedDecision(interaction, event.getEventId()));
    }

    public void expire(InteractionSessionEntity interaction) {
        closureTxService.expire(interaction);
    }

    /**
     * 新协议不兼容旧检查点时，原子结束整条等待链，避免用户点击审批后卡在无法恢复的状态。
     */
    public void supersedeCheckpointProtocol(InteractionSessionEntity interaction) {
        closureTxService.supersedeCheckpointProtocol(interaction);
    }

    private void approve(InteractionSessionEntity interaction, CapabilityInvocationEntity invocation, String eventId) {
        if (!invocationRepo.changeStatus(invocation.getId(), CapabilityInvocationStatus.PENDING_APPROVAL,
                CapabilityInvocationStatus.APPROVED, eventId, null, null)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Tool调用审批状态已变化");
        }
        if (!runRepo.changeStatus(interaction.getRunId(), ExecutionRunStatus.WAITING_HUMAN,
                ExecutionRunStatus.RESUMING, "HITL_APPROVED", null)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "执行运行状态已变化");
        }
        // 审批提交后即可订阅恢复事件，不能让浏览器等待 MQ 消费者才看到消息继续生成。
        conversationService.resumeFromHuman(interaction.getAssistantMessageId());
    }

    private void close(InteractionSessionEntity interaction, CapabilityInvocationEntity invocation, String eventId,
                       InteractionDecision decision) {
        CapabilityInvocationStatus target = decision == InteractionDecision.REJECT
                ? CapabilityInvocationStatus.REJECTED : CapabilityInvocationStatus.CANCELLED;
        String errorCode = decision == InteractionDecision.REJECT ? "USER_REJECTED" : "USER_CANCELLED";
        if (!invocationRepo.changeStatus(invocation.getId(), CapabilityInvocationStatus.PENDING_APPROVAL,
                target, eventId, null, errorCode)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Tool调用审批状态已变化");
        }
        if (!checkpointRepo.supersede(interaction.getCheckpointId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "HITL检查点状态已变化");
        }
        if (!runRepo.changeStatus(interaction.getRunId(), ExecutionRunStatus.WAITING_HUMAN,
                ExecutionRunStatus.CANCELLED, errorCode, OffsetDateTime.now())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "执行运行状态已变化");
        }
        String message = decision == InteractionDecision.REJECT
                ? "你已拒绝本次操作，本轮任务已结束" : "你已取消本轮待确认操作";
        conversationService.cancelWaiting(interaction.getAssistantMessageId(), errorCode, message);
    }

    private static void requireDecision(InteractionSessionEntity interaction, HitlDecisionTxBO tx) {
        if (interaction.getInteractionType() != InteractionSessionType.ACTION_APPROVAL) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "当前交互不支持审批决策");
        }
        if (!Objects.equals(interaction.getApprovalFingerprint(), tx.getFingerprint())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "审批指纹不匹配，操作内容可能已变化");
        }
    }

    private void requireActiveCheckpoint(InteractionSessionEntity interaction) {
        ExecutionCheckpointEntity checkpoint = checkpointRepo.findById(interaction.getCheckpointId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "HITL检查点不存在"));
        if (checkpoint.getCheckpointVersion() == null
                || checkpoint.getCheckpointVersion() < AgentScopeCheckpoint.CURRENT_VERSION) {
            supersedeCheckpointProtocol(interaction);
            throw new BusinessException(HttpStatusEnum.CONFLICT, "审批检查点协议已升级，请重新发起任务");
        }
        if (checkpoint.getCheckpointVersion() > AgentScopeCheckpoint.CURRENT_VERSION) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "审批检查点协议高于当前服务版本，请稍后重试");
        }
        if (checkpoint.getStatus() != ExecutionCheckpointStatus.ACTIVE
                || checkpoint.getExpiresAt().isBefore(OffsetDateTime.now())
                || !Objects.equals(checkpoint.getChecksum(), interaction.getApprovalFingerprint())
                || !Objects.equals(checkpoint.getRunId(), interaction.getRunId())
                || !Objects.equals(checkpoint.getAssistantMessageId(), interaction.getAssistantMessageId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "HITL检查点已失效或内容已变化");
        }
    }

    private static boolean isSameCommittedDecision(InteractionSessionEntity interaction, String eventId) {
        return Objects.equals(interaction.getIdempotencyKey(), eventId)
                && (interaction.getStatus() == InteractionSessionStatus.RESOLVED
                || interaction.getStatus() == InteractionSessionStatus.REJECTED
                || interaction.getStatus() == InteractionSessionStatus.CANCELLED);
    }
}
