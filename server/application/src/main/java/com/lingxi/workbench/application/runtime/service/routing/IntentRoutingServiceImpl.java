package com.lingxi.workbench.application.runtime.service.routing;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingReason;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidence;
import com.lingxi.workbench.application.runtime.model.IntentRoutingEvidenceCandidate;
import com.lingxi.workbench.application.runtime.model.IntentRoutingResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;

/** 组织冻结配置加载、候选识别与澄清恢复；所有路径在末尾统一提交。 */
@Service
@RequiredArgsConstructor
public class IntentRoutingServiceImpl implements IntentRoutingService {
    private final IntentTreeRuntimeRepo intentTreeRepo;
    private final IntentCandidateRetriever candidateRetriever;
    private final IntentSemanticDecisionService semanticDecisionService;
    private final IntentClarificationService clarificationService;
    private final IntentRoutingTxServiceImpl decisionPersistenceService;

    @Override
    public IntentRoutingResult route(ConversationContext context, ClarificationResponseRequest response) {
        AppRuntimeSnapshot application = context.getApplicationSnapshot();
        ApplicationIntentBinding binding = application.definition().intentRouting();
        if (Objects.isNull(binding) || !binding.isEnabled()) {
            return decisionPersistenceService.append(context, null,
                    IntentRoutingOutcome.completed(IntentRoutingResult.skipped(context.routingQuery())));
        }
        RuntimeIntentTreeVersionEntity version = intentTreeRepo.findVersion(binding.getIntentTreeId(), binding.getIntentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Application锁定的意图树版本不存在"));
        if (version.getDefinitionSnapshot().schemaVersion() != INTENT_TREE_SCHEMA_VERSION) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "意图树发布定义版本不受支持");
        }
        IntentRoutingConfig configuration = new IntentRoutingConfig(application, version);
        Optional<IntentRoutingSessionEntity> pending = clarificationService.findPending(configuration, context, response);
        IntentRoutingOutcome outcome = pending.isPresent()
                ? clarificationService.resume(configuration, pending.get(), context.getOriginalQuery(), response)
                : routeFresh(configuration, context);
        return decisionPersistenceService.append(context, configuration, outcome);
    }

    private IntentRoutingOutcome routeFresh(IntentRoutingConfig configuration, ConversationContext context) {
        String query = context.routingQuery();
        IntentCandidateSet candidateSet = candidateRetriever.retrieve(configuration.getVersion(), query);
        List<IntentCandidate> candidates = candidateSet.candidates();
        if (candidates.isEmpty()) {
            return IntentRoutingOutcome.completed(IntentRoutingResult.deferUnmatched(query, IntentRoutingReason.NO_CANDIDATE));
        }
        IntentSemanticDecisionResult semantic = semanticDecisionService.decide(context, configuration.getVersion(), candidates);
        IntentSemanticDecision decision = semantic.getDecision();
        IntentRoutingOutcome outcome = switch (decision.getAction()) {
            case SELECT -> select(configuration, context, candidates, decision, IntentRoutingReason.ROUTED_BY_SEMANTIC);
            case CLARIFY -> clarify(configuration, context, candidates, decision);
            case DEFER -> IntentRoutingOutcome.completed(defer(configuration, query, candidates, semanticDeferReason(decision.getReasonCode())));
        };
        return outcome.toBuilder().candidates(candidates).semanticResult(semantic).build();
    }

    private IntentRoutingOutcome select(IntentRoutingConfig configuration, ConversationContext context,
                                         List<IntentCandidate> candidates, IntentSemanticDecision decision, IntentRoutingReason reason) {
        // 候选编码和槽位已由语义决策服务验证；这里仅处理可选的业务映射。
        IntentCandidate selected = candidates.stream().filter(candidate -> candidate.intentCode().equals(decision.getSelectedIntentCode()))
                .findFirst().orElseThrow();
        if (configuration.findMapping(selected.intentCode()).isEmpty()) {
            return IntentRoutingOutcome.completed(defer(configuration, context.routingQuery(), List.of(selected), IntentRoutingReason.UNMAPPED_CANDIDATE));
        }
        return clarificationService.select(configuration, context, decision, evidence(reason, List.of(selected)));
    }

    private IntentRoutingOutcome clarify(IntentRoutingConfig configuration, ConversationContext context,
                                          List<IntentCandidate> candidates, IntentSemanticDecision decision) {
        if (CharSequenceUtil.isNotBlank(decision.getSelectedIntentCode())) {
            return select(configuration, context, candidates, decision, IntentRoutingReason.SEMANTIC_CLARIFICATION);
        }
        List<IntentCandidate> alternatives = candidates.stream()
                .filter(candidate -> decision.alternativeIntentCodes().contains(candidate.intentCode())).toList();
        if (alternatives.stream().anyMatch(candidate -> configuration.findMapping(candidate.intentCode()).isEmpty())) {
            return IntentRoutingOutcome.completed(defer(configuration, context.routingQuery(), alternatives, IntentRoutingReason.UNMAPPED_CANDIDATE));
        }
        return clarificationService.disambiguate(configuration, context, alternatives,
                evidence(IntentRoutingReason.SEMANTIC_CLARIFICATION, alternatives));
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

    private static IntentRoutingResult defer(
            IntentRoutingConfig configuration,
            String query,
            List<IntentCandidate> candidates,
            IntentRoutingReason reason) {
        Map<String, CapabilityDefinition> capabilitiesByCode = configuration.getApplication()
                .definition().capabilityCatalog().capabilities().stream()
                .collect(Collectors.toMap(CapabilityDefinition::getCode, Function.identity()));
        List<IntentRoutingEvidenceCandidate> evidenceCandidates = candidates.stream()
                .map(candidate -> toEvidenceCandidate(configuration, capabilitiesByCode, candidate))
                .toList();
        return IntentRoutingResult.defer(query, IntentRoutingEvidence.builder()
                .reason(reason)
                .candidates(evidenceCandidates)
                .build());
    }

    private static IntentRoutingEvidenceCandidate toEvidenceCandidate(
            IntentRoutingConfig configuration,
            Map<String, CapabilityDefinition> capabilitiesByCode,
            IntentCandidate candidate) {
        ApplicationIntentMapping mapping = configuration.findMapping(candidate.intentCode()).orElse(null);
        String capabilityCode = Objects.isNull(mapping) ? null : mapping.getCapabilityCode();
        CapabilityDefinition capability = capabilitiesByCode.get(capabilityCode);
        IntentNodeDefinition node = configuration.getNodesByCode().get(candidate.intentCode());
        return IntentRoutingEvidenceCandidate.builder()
                .intentCode(candidate.intentCode())
                .intentName(Objects.isNull(node) ? candidate.intentCode() : node.getName())
                .similarity(candidate.similarity())
                .capabilityCode(capabilityCode)
                .capabilityDescription(Objects.isNull(capability) ? null : capability.getDescription())
                .build();
    }

    private static IntentRoutingEvidence evidence(IntentRoutingReason reason,
                                                  List<IntentCandidate> candidates) {
        List<IntentRoutingEvidenceCandidate> evidenceCandidates = candidates.stream()
                .map(candidate -> IntentRoutingEvidenceCandidate.builder()
                        .intentCode(candidate.intentCode())
                        .similarity(candidate.similarity())
                        .build())
                .toList();
        return IntentRoutingEvidence.builder()
                .reason(reason)
                .candidates(evidenceCandidates)
                .build();
    }

}
