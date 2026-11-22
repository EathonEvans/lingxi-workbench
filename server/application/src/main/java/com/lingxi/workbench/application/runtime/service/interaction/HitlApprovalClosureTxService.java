package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.common.enums.CapabilityInvocationStatus;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionRunStatus;
import com.lingxi.workbench.application.runtime.model.entity.CapabilityInvocationEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.repo.CapabilityInvocationRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.ExecutionRunRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/** 终止人工等待链时使用独立事务，确保入口返回冲突后终态仍可提交。 */
@Service
@RequiredArgsConstructor
public class HitlApprovalClosureTxService {

    private final InteractionSessionRepo interactionRepo;
    private final CapabilityInvocationRepo invocationRepo;
    private final ExecutionCheckpointRepo checkpointRepo;
    private final ExecutionRunRepo runRepo;
    private final ConversationService conversationService;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(InteractionSessionEntity interaction) {
        if (!interactionRepo.expire(interaction.getId(), interaction.getRevision())) {
            return;
        }
        // 等待链终止后收敛对应站内通知，避免用户点击过期的待确认提醒。
        notificationService.markInteractionNotificationsRead(interaction.getUserId(), interaction.getId());
        CapabilityInvocationEntity invocation = invocationRepo.findByInteractionId(interaction.getId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Tool调用意图不存在"));
        if (!invocationRepo.changeStatus(invocation.getId(), CapabilityInvocationStatus.PENDING_APPROVAL,
                CapabilityInvocationStatus.EXPIRED, null, null, "APPROVAL_EXPIRED")) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Tool调用审批状态已变化");
        }
        if (!checkpointRepo.expire(interaction.getCheckpointId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "HITL检查点状态已变化");
        }
        if (!runRepo.changeStatus(interaction.getRunId(), ExecutionRunStatus.WAITING_HUMAN,
                ExecutionRunStatus.EXPIRED, "HITL_EXPIRED", OffsetDateTime.now())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "执行运行状态已变化");
        }
        conversationService.cancelWaiting(interaction.getAssistantMessageId(), "APPROVAL_EXPIRED", "审批已过期，本轮任务已结束");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void supersedeCheckpointProtocol(InteractionSessionEntity interaction) {
        if (!interactionRepo.failForCheckpointProtocol(interaction.getId(), interaction.getRevision())) {
            return;
        }
        notificationService.markInteractionNotificationsRead(interaction.getUserId(), interaction.getId());
        CapabilityInvocationEntity invocation = invocationRepo.findByInteractionId(interaction.getId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Tool调用意图不存在"));
        if (!invocationRepo.changeStatus(invocation.getId(), CapabilityInvocationStatus.PENDING_APPROVAL,
                CapabilityInvocationStatus.FAILED, null, null, "CHECKPOINT_PROTOCOL_SUPERSEDED")) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Tool调用审批状态已变化");
        }
        if (!checkpointRepo.supersede(interaction.getCheckpointId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "HITL检查点状态已变化");
        }
        if (!runRepo.changeStatus(interaction.getRunId(), ExecutionRunStatus.WAITING_HUMAN,
                ExecutionRunStatus.FAILED, "CHECKPOINT_PROTOCOL_SUPERSEDED", OffsetDateTime.now())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "执行运行状态已变化");
        }
        conversationService.cancelWaiting(interaction.getAssistantMessageId(), "CHECKPOINT_PROTOCOL_SUPERSEDED",
                "运行检查点协议已升级，本轮待审批任务无法恢复，请重新发起任务");
    }
}
