package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateRequest;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeApplicationVersionEntity;
import com.lingxi.workbench.application.runtime.repo.ApplicationRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;
import com.lingxi.workbench.application.runtime.service.routing.IntentCandidateRetriever;
import com.lingxi.workbench.application.runtime.service.routing.IntentSemanticDecisionService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;

/** 使用生产候选召回和语义决策能力执行一次不落业务事实的 Application 草稿或锁定版本测试。 */
@Service
@RequiredArgsConstructor
public class IntentRoutingPreviewService {

    private static final LoginUser PREVIEW_USER = new LoginUser(0L, "intent-preview", "意图草稿测试", null);
    private static final ChatTurnBO PREVIEW_TURN = new ChatTurnBO(0L, 0L, 0L, 1);

    private final IntentTreeRuntimeRepo intentTreeRuntimeRepo;
    private final ApplicationRuntimeRepo applicationRuntimeRepo;
    private final IntentCandidateRetriever candidateRetriever;
    private final IntentSemanticDecisionService semanticDecisionService;
    private final RuntimeVariablesInitializer runtimeVariablesInitializer;

    public IntentRoutingPreviewResult preview(IntentRoutingPreviewRequest request) {
        AppRuntimeSnapshot application = createApplicationSnapshot(request);
        return preview(application, request.query().trim());
    }

    public IntentRoutingPreviewResult previewVersion(IntentRoutingVersionPreviewRequest request) {
        RuntimeApplicationEntity application = applicationRuntimeRepo.findById(request.applicationId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Application不存在"));
        RuntimeApplicationVersionEntity version = applicationRuntimeRepo
                .findVersion(request.applicationId(), request.applicationVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "ApplicationVersion不存在"));
        AppRuntimeSnapshot snapshot = createApplicationSnapshot(application, version);
        return preview(snapshot, request.query().trim());
    }

    private IntentRoutingPreviewResult preview(AppRuntimeSnapshot application, String query) {
        ApplicationIntentBinding binding = requireRoutingBinding(application.definition());
        RuntimeIntentTreeVersionEntity version = intentTreeRuntimeRepo
                .findVersion(binding.getIntentTreeId(), binding.getIntentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Application锁定的IntentTreeVersion不存在"));
        IntentTreeDefinition definition = version.getDefinitionSnapshot();
        if (Objects.isNull(definition) || definition.schemaVersion() != INTENT_TREE_SCHEMA_VERSION) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Application锁定的IntentTreeVersion协议不受支持");
        }
        IntentRoutingPolicy policy = requireRoutingPolicy(definition);
        IntentCandidateSet candidateSet = candidateRetriever.retrieve(new IntentCandidateRequest(
                binding.getIntentTreeId(), binding.getIntentTreeVersionId(), query));
        if (candidateSet.candidates().isEmpty()) {
            return emptyCandidateResult(candidateSet.retrievedCandidates(), policy);
        }

        ConversationContext context = createConversationContext(application, query);
        IntentSemanticDecisionResult semanticResult = semanticDecisionService.decide(
                context, version, candidateSet.candidates());
        return result(application.definition(), candidateSet, semanticResult);
    }

    private IntentRoutingPreviewResult result(ApplicationDefinition definition, IntentCandidateSet candidateSet,
                                               IntentSemanticDecisionResult semanticResult) {
        IntentSemanticDecision decision = semanticResult.getDecision();
        GateDecision gate = gate(definition.intentRouting(), decision);
        return IntentRoutingPreviewResult.builder()
                .candidates(toCandidates(candidateSet.candidates()))
                .retrievedCandidates(toCandidates(candidateSet.retrievedCandidates()))
                .semanticAction(decision.getAction())
                .semanticReasonCode(decision.getReasonCode())
                .selectedIntentCode(decision.getSelectedIntentCode())
                .alternativeIntentCodes(decision.alternativeIntentCodes())
                .recognizedSlots(decision.recognizedSlots())
                .finalAction(gate.action())
                .finalReasonCode(gate.reason())
                .targetType(gate.targetType())
                .capabilityCode(gate.capabilityCode())
                .modelInstanceId(semanticResult.getModelInstanceId())
                .promptVersionId(semanticResult.getPromptVersionId())
                .tokenUsage(Objects.requireNonNullElseGet(semanticResult.getTokenUsage(), TokenUsage::empty))
                .modelCalls(semanticResult.getModelCalls())
                .retryCount(semanticResult.getRetryCount())
                .durationMillis(semanticResult.getDurationMillis())
                .build();
    }

    private static GateDecision gate(ApplicationIntentBinding binding, IntentSemanticDecision decision) {
        if (decision.getAction() == IntentSemanticAction.CLARIFY) {
            return new GateDecision(IntentRoutingAction.CLARIFICATION,
                    IntentRoutingReason.SEMANTIC_CLARIFICATION, null, null);
        }
        if (decision.getAction() == IntentSemanticAction.DEFER) {
            return new GateDecision(IntentRoutingAction.DEFER_TO_AGENT,
                    semanticDeferReason(decision.getReasonCode()), null, null);
        }
        ApplicationIntentMapping mapping = binding.mappings().stream()
                .filter(item -> Objects.equals(item.getIntentCode(), decision.getSelectedIntentCode()))
                .findFirst()
                .orElse(null);
        if (Objects.isNull(mapping)) {
            return new GateDecision(IntentRoutingAction.DEFER_TO_AGENT,
                    IntentRoutingReason.UNMAPPED_CANDIDATE, null, null);
        }
        IntentRoutingAction action = switch (mapping.getTargetType()) {
            case AGENT -> IntentRoutingAction.EXECUTE_AGENT;
            case WORKFLOW -> IntentRoutingAction.EXECUTE_WORKFLOW;
            case FIXED_RESPONSE -> IntentRoutingAction.FIXED_RESPONSE;
        };
        return new GateDecision(action, IntentRoutingReason.ROUTED_BY_SEMANTIC,
                mapping.getTargetType(), mapping.getCapabilityCode());
    }

    private static IntentRoutingReason semanticDeferReason(IntentDecisionReason reason) {
        if (reason == IntentDecisionReason.SEMANTIC_DECISION_FAILED) {
            return IntentRoutingReason.SEMANTIC_DECISION_FAILED;
        }
        if (reason == IntentDecisionReason.MODEL_BUDGET_EXHAUSTED) {
            return IntentRoutingReason.MODEL_BUDGET_EXHAUSTED;
        }
        return IntentRoutingReason.SEMANTIC_DEFER;
    }

    private ConversationContext createConversationContext(AppRuntimeSnapshot application, String query) {
        return ConversationContext.builder()
                .applicationSnapshot(application)
                .loginUser(PREVIEW_USER)
                .turn(PREVIEW_TURN)
                .originalQuery(query)
                .rewrittenQuery(query)
                .recentMessages(List.of())
                .summary("")
                .longTermMemory("")
                .budget(ContextBudgetPolicy.defaults())
                .runtimeVariables(runtimeVariablesInitializer.initialize(PREVIEW_USER, query, 0L))
                .build();
    }

    private static AppRuntimeSnapshot createApplicationSnapshot(IntentRoutingPreviewRequest request) {
        try {
            return new AppRuntimeSnapshot(request.applicationId(), request.applicationCode(), 0L, request.definition());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Application草稿不可测试：" + exception.getMessage());
        }
    }

    private static AppRuntimeSnapshot createApplicationSnapshot(
            RuntimeApplicationEntity application, RuntimeApplicationVersionEntity version) {
        try {
            return new AppRuntimeSnapshot(application.getId(), application.getCode(), version.getId(),
                    version.getDefinitionSnapshot());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "ApplicationVersion不可测试：" + exception.getMessage());
        }
    }

    private static ApplicationIntentBinding requireRoutingBinding(ApplicationDefinition definition) {
        ApplicationIntentBinding binding = definition.intentRouting();
        if (Objects.isNull(binding) || !binding.isEnabled()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Application草稿未启用意图路由");
        }
        return binding;
    }

    private static IntentRoutingPolicy requireRoutingPolicy(IntentTreeDefinition definition) {
        if (definition.routingPolicy() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "Application锁定的IntentTreeVersion缺少路由策略");
        }
        return definition.routingPolicy();
    }

    private static IntentRoutingPreviewResult emptyCandidateResult(
            List<IntentCandidate> retrievedCandidates, IntentRoutingPolicy policy) {
        return IntentRoutingPreviewResult.builder()
                .candidates(List.of())
                .retrievedCandidates(toCandidates(retrievedCandidates))
                .semanticAction(IntentSemanticAction.DEFER)
                .semanticReasonCode(IntentDecisionReason.NO_SUITABLE_CANDIDATE)
                .alternativeIntentCodes(List.of())
                .recognizedSlots(List.of())
                .finalAction(IntentRoutingAction.DEFER_TO_AGENT)
                .finalReasonCode(IntentRoutingReason.NO_CANDIDATE)
                .modelInstanceId(policy.chatModelInstanceId())
                .promptVersionId(policy.decisionPromptVersionId())
                .tokenUsage(TokenUsage.empty())
                .build();
    }

    private static List<IntentRoutingPreviewCandidate> toCandidates(List<IntentCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> new IntentRoutingPreviewCandidate(candidate.intentCode(), candidate.getName(),
                        candidate.similarity(), candidate.getRank()))
                .toList();
    }

    /**
     * @param action         最终确定性动作
     * @param reason         最终稳定原因
     * @param targetType     可选执行目标类型
     * @param capabilityCode 可选锁定能力编码
     */
    private record GateDecision(IntentRoutingAction action, IntentRoutingReason reason,
                                IntentExecutionTargetType targetType, String capabilityCode) {
    }
}
