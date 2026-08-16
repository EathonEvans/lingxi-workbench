package com.lingxi.workbench.application.runtime.service.interaction;

import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.HitlDecisionTxBO;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.model.message.HitlResumeMessage;
import com.lingxi.workbench.application.runtime.model.request.InteractionDecisionRequest;
import com.lingxi.workbench.application.runtime.model.vo.InteractionDecisionVO;
import com.lingxi.workbench.application.runtime.model.vo.PendingInteractionVO;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.chat.common.constants.ConversationMqConstants;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.mq.MqProducer;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** HITL 决策入口与刷新恢复查询。 */
@Service
@RequiredArgsConstructor
public class HitlDecisionService {

    private final InteractionSessionRepo interactionRepo;
    private final MqProducer<Object> mqProducer;

    public InteractionDecisionVO decide(Long interactionId, Long userId, String idempotencyKey,
                                        InteractionDecisionRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Idempotency-Key不能为空");
        }
        InteractionSessionEntity interaction = interactionRepo.findOwned(interactionId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "HITL交互不存在"));
        HitlResumeMessage event = HitlResumeMessage.builder()
                .eventId(idempotencyKey.trim())
                .interactionId(interaction.getId())
                .conversationId(interaction.getConversationId())
                .assistantMessageId(interaction.getAssistantMessageId())
                .appVersionId(interaction.getApplicationVersionId())
                .build();
        HitlDecisionTxBO tx = HitlDecisionTxBO.builder()
                .event(event)
                .userId(userId)
                .decision(request.getDecision())
                .expectedRevision(request.getExpectedRevision())
                .fingerprint(request.getFingerprint())
                .build();
        TransactionSendResult result = mqProducer.sendTransaction(
                ConversationMqConstants.HITL_RESUME_TOPIC, event.getEventId(), event, tx);
        if (result.getLocalTransactionState() != LocalTransactionState.COMMIT_MESSAGE) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "HITL决策事务未提交");
        }
        InteractionSessionEntity decided = interactionRepo.findOwned(interactionId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "HITL交互不存在"));
        return new InteractionDecisionVO(String.valueOf(decided.getId()), decided.getStatus(), decided.getRevision());
    }

    @Transactional(readOnly = true)
    public List<PendingInteractionVO> pending(Long conversationId, Long userId) {
        return interactionRepo.findPendingOwned(conversationId, userId, InteractionSessionType.ACTION_APPROVAL).stream()
                .map(HitlDecisionService::toPending)
                .toList();
    }

    private static PendingInteractionVO toPending(InteractionSessionEntity interaction) {
        return PendingInteractionVO.builder()
                .interactionId(String.valueOf(interaction.getId()))
                .assistantMessageId(String.valueOf(interaction.getAssistantMessageId()))
                .interactionType(interaction.getInteractionType())
                .title(interaction.getContext().getTitle())
                .summary(interaction.getContext().getSummary())
                .riskLevel(interaction.getContext().getRiskLevel())
                .resourceScope(interaction.getContext().getResourceScope())
                .fingerprint(interaction.getApprovalFingerprint())
                .revision(interaction.getRevision())
                .expiresAt(interaction.getExpiresAt())
                .build();
    }
}
