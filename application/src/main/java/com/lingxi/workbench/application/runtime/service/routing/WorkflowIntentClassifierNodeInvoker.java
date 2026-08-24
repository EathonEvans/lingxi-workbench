package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateRequest;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.IntentRecognizedSlot;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticSlotValue;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.exception.IntentClassifierNodeException;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeInvocation;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeResult;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeStatus;
import com.lingxi.workbench.workflow.model.IntentClassifierRecognizedSlot;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 将 Workflow 意图分类节点绑定到 V2 候选召回和结构化语义决策。 */
@Component
@RequiredArgsConstructor
public class WorkflowIntentClassifierNodeInvoker {

    private final IntentCandidateRetriever candidateRetriever;
    private final IntentTreeRuntimeRepo intentTreeRuntimeRepo;
    private final IntentSemanticDecisionService semanticDecisionService;

    /**
     * 分类节点只返回状态变量，不解析或执行 Application 能力映射。
     * 每一次真实模型调用由共享 ChatExecutionContext 原子占用预算并累计 Token。
     */
    public IntentClassifierNodeResult invoke(IntentClassifierNodeInvocation invocation,
                                             ChatExecutionContext executionContext) {
        requireInvocation(invocation, executionContext);
        RuntimeIntentTreeVersionEntity version = intentTreeRuntimeRepo
                .findVersion(invocation.getIntentTreeId(), invocation.getIntentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Workflow锁定的意图树版本不存在"));
        IntentCandidateSet candidateSet = candidateRetriever.retrieve(new IntentCandidateRequest(
                invocation.getIntentTreeId(), invocation.getIntentTreeVersionId(), invocation.getQuery()));
        if (candidateSet.candidates().isEmpty()) {
            return emptyCandidateResult(version);
        }

        ConversationContext scopedContext = executionContext.getConversationContext()
                .forkForQuery(invocation.getQuery());
        try {
            IntentSemanticDecisionResult decisionResult = semanticDecisionService.decideForWorkflow(
                    scopedContext, version, candidateSet.candidates(), executionContext);
            return toWorkflowResult(candidateSet.candidates(), decisionResult);
        } catch (IntentSemanticDecisionException exception) {
            IntentClassifierNodeResult result = toWorkflowResult(
                    candidateSet.candidates(), exception.getDecisionResult());
            throw new IntentClassifierNodeException("Workflow意图分类模型调用失败", result, exception);
        }
    }

    private static IntentClassifierNodeResult emptyCandidateResult(RuntimeIntentTreeVersionEntity version) {
        IntentRoutingPolicy policy = Objects.requireNonNull(
                version.getDefinitionSnapshot().routingPolicy(), "意图树缺少路由策略");
        return IntentClassifierNodeResult.builder()
                .status(IntentClassifierNodeStatus.DEFERRED)
                .reasonCode(IntentDecisionReason.NO_SUITABLE_CANDIDATE.getCode())
                .candidateCodes(List.of())
                .recognizedSlots(List.of())
                .modelInstanceId(policy.chatModelInstanceId())
                .promptVersionId(policy.decisionPromptVersionId())
                .tokenUsage(TokenUsage.empty())
                .modelCalls(0)
                .retryCount(0)
                .durationMillis(0L)
                .build();
    }

    private static IntentClassifierNodeResult toWorkflowResult(List<IntentCandidate> candidates,
                                                               IntentSemanticDecisionResult decisionResult) {
        IntentSemanticDecision decision = Objects.requireNonNull(
                decisionResult.getDecision(), "意图语义决策结果不能为空");
        String selectedCode = decision.getSelectedIntentCode();
        Optional<IntentCandidate> evidence = candidates.stream()
                .filter(candidate -> Objects.equals(candidate.intentCode(), selectedCode))
                .findFirst()
                .or(() -> candidates.stream().findFirst());
        return IntentClassifierNodeResult.builder()
                .status(toWorkflowStatus(decision))
                .intentCode(selectedCode)
                .reasonCode(decision.getReasonCode().getCode())
                .similarity(evidence.map(IntentCandidate::similarity).orElse(null))
                .candidateCodes(candidates.stream().map(IntentCandidate::intentCode).toList())
                .recognizedSlots(decision.recognizedSlots().stream()
                        .map(WorkflowIntentClassifierNodeInvoker::toWorkflowSlot)
                        .toList())
                .modelInstanceId(decisionResult.getModelInstanceId())
                .promptVersionId(decisionResult.getPromptVersionId())
                .tokenUsage(decisionResult.getTokenUsage())
                .modelCalls(decisionResult.getModelCalls())
                .retryCount(decisionResult.getRetryCount())
                .durationMillis(decisionResult.getDurationMillis())
                .build();
    }

    private static IntentClassifierNodeStatus toWorkflowStatus(IntentSemanticDecision decision) {
        return switch (decision.getAction()) {
            case SELECT -> IntentClassifierNodeStatus.SELECTED;
            case CLARIFY -> IntentClassifierNodeStatus.CLARIFICATION_REQUIRED;
            case DEFER -> IntentClassifierNodeStatus.DEFERRED;
        };
    }

    private static IntentClassifierRecognizedSlot toWorkflowSlot(IntentRecognizedSlot slot) {
        IntentSemanticSlotValue value = slot.getValue();
        return IntentClassifierRecognizedSlot.builder()
                .slotCode(slot.getSlotCode())
                .stringValue(value.getStringValue())
                .numberValue(value.getNumberValue())
                .dateValue(value.getDateValue())
                .booleanValue(value.getBooleanValue())
                .build();
    }

    private static void requireInvocation(IntentClassifierNodeInvocation invocation,
                                          ChatExecutionContext executionContext) {
        if (Objects.isNull(invocation) || Objects.isNull(executionContext)
                || Objects.isNull(executionContext.getConversationContext())
                || Objects.isNull(invocation.getIntentTreeId())
                || Objects.isNull(invocation.getIntentTreeVersionId())
                || Objects.isNull(invocation.getQuery()) || invocation.getQuery().isBlank()) {
            throw new IllegalArgumentException("Workflow意图分类调用参数不完整");
        }
    }
}
