package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentClarificationContext;
import com.lingxi.workbench.application.runtime.model.IntentRecognizedSlot;
import com.lingxi.workbench.application.runtime.model.IntentRoutingDecisionCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingDecisionEntity;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingDecisionRepo;
import com.lingxi.workbench.application.runtime.repo.IntentRoutingSessionRepo;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 模型调用结束后，将本轮决策和会话最终状态一起提交，不持久化中间状态。 */
@Service
@RequiredArgsConstructor
public class IntentRoutingTxServiceImpl {
    private final IntentRoutingDecisionRepo decisionRepo;
    private final IntentRoutingSessionRepo sessionRepo;

    @Transactional
    public IntentRoutingResult append(ConversationContext context, IntentRoutingConfig configuration, IntentRoutingOutcome outcome) {
        IntentRoutingResult routing = outcome.getResult();
        IntentSemanticDecisionResult semanticResult = outcome.getSemanticResult();
        List<IntentCandidate> candidates = outcome.getCandidates();
        TokenUsage usage = Objects.isNull(semanticResult)
                ? TokenUsage.empty() : Objects.requireNonNullElseGet(semanticResult.getTokenUsage(), TokenUsage::empty);
        IntentRoutingReason reason = routing.getRoutingEvidence().getReason();
        // 首次识别保留模型选择（包括未映射叶子），恢复路径使用本轮已确认的意图。
        String selectedIntentCode = Objects.isNull(semanticResult)
                ? routing.getIntentCode() : semanticResult.getDecision().getSelectedIntentCode();
        RuntimeIntentTreeVersionEntity version = Objects.isNull(configuration) ? null : configuration.getVersion();
        IntentRoutingDecisionEntity decision = IntentRoutingDecisionEntity.builder()
                .conversationId(context.getTurn().conversationId())
                .userId(context.getLoginUser().id())
                .userMessageId(context.getTurn().userMessageId())
                .assistantMessageId(context.getTurn().assistantMessageId())
                .applicationId(context.getApplicationSnapshot().applicationId())
                .applicationVersionId(context.getApplicationSnapshot().versionId())
                .intentTreeId(Objects.isNull(version) ? null : version.getIntentTreeId())
                .intentTreeVersionId(Objects.isNull(version) ? null : version.getId())
                .embeddingModelInstanceId(Objects.isNull(version) ? null : version.getEmbeddingModelInstanceId())
                .chatModelInstanceId(Objects.isNull(semanticResult) ? null : semanticResult.getModelInstanceId())
                .promptVersionId(Objects.isNull(semanticResult) ? null : semanticResult.getPromptVersionId())
                .action(routing.getAction())
                .reason(reason)
                .selectedIntentCode(selectedIntentCode)
                .capabilityCode(Objects.isNull(routing.getMapping()) ? null : routing.getMapping().getCapabilityCode())
                .candidateEvidence(decisionCandidates(configuration, candidates))
                .semanticDecision(semanticDecisionSnapshot(semanticResult))
                .inputTokens(usage.inputTokens())
                .outputTokens(usage.outputTokens())
                .totalTokens(usage.totalTokens())
                .modelCalls(Objects.isNull(semanticResult) ? 0 : semanticResult.getModelCalls())
                .retryCount(Objects.isNull(semanticResult) ? 0 : semanticResult.getRetryCount())
                .durationMillis(Objects.isNull(semanticResult) ? 0L : semanticResult.getDurationMillis())
                .build();

        decisionRepo.insert(decision);
        saveSession(decision, outcome);
        if (Objects.nonNull(routing.getClarification())) {
            routing.getClarification().setClarificationId(outcome.getSession().getId().toString());
        }
        Long decisionId = decision.getId();
        return routing.toBuilder()
                .decisionId(decisionId)
                .tokenUsage(usage)
                .modelCalls(decision.getModelCalls())
                .retryCount(decision.getRetryCount())
                .durationMillis(decision.getDurationMillis())
                .build();
    }

    private void saveSession(IntentRoutingDecisionEntity decision, IntentRoutingOutcome outcome) {
        IntentRoutingSessionEntity session = outcome.getSession();
        if (Objects.isNull(session)) return;
        IntentClarificationContext context = session.getContext();
        if (Objects.isNull(context.getDecisionId())) {
            context.setDecisionId(decision.getId());
            context.setOriginalMessageId(decision.getUserMessageId());
        }
        if (Objects.isNull(session.getId())) {
            sessionRepo.insert(session);
        } else {
            IntentRoutingOutcome.PendingState expected = outcome.getExpectedState();
            // 类型和轮次一起匹配，避免消歧转补槽位时轮次重置后接受旧回复。
            sessionRepo.updatePending(session, expected.type(), expected.round());
        }
    }

    private static List<IntentRoutingDecisionCandidate> decisionCandidates(
            IntentRoutingConfig configuration, List<IntentCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> new IntentRoutingDecisionCandidate(
                        candidate.intentCode(), candidate.getRank(), candidate.similarity(),
                        Objects.isNull(configuration) ? null : configuration.findMapping(candidate.intentCode())
                                .map(ApplicationIntentMapping::getCapabilityCode)
                                .orElse(null)))
                .toList();
    }

    private static IntentSemanticDecisionSnapshot semanticDecisionSnapshot(
            IntentSemanticDecisionResult semanticResult) {
        if (Objects.isNull(semanticResult) || Objects.isNull(semanticResult.getDecision())) {
            return null;
        }
        IntentSemanticDecision decision = semanticResult.getDecision();
        return IntentSemanticDecisionSnapshot.builder()
                .action(decision.getAction())
                .reason(decision.getReasonCode())
                .selectedIntentCode(decision.getSelectedIntentCode())
                .alternativeIntentCodes(decision.alternativeIntentCodes())
                .recognizedSlotCodes(decision.recognizedSlots().stream()
                        .map(IntentRecognizedSlot::getSlotCode)
                        .toList())
                .build();
    }
}
