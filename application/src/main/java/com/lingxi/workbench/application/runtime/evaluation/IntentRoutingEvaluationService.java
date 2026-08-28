package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingAction;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lingxi.workbench.workflow.common.constants.IntentRoutingConstants.INTENT_TREE_SCHEMA_VERSION;

/**
 * 通过正在运行的生产决策链生成 V2 版本评测报告，不写业务数据。
 */
@Service
public class IntentRoutingEvaluationService {

    private static final int MINIMUM_PRODUCTION_SAMPLE_COUNT = 300;
    private static final Set<IntentRoutingAction> EXECUTABLE_ACTIONS = EnumSet.of(
            IntentRoutingAction.EXECUTE_AGENT,
            IntentRoutingAction.EXECUTE_WORKFLOW,
            IntentRoutingAction.FIXED_RESPONSE);

    private final IntentEvaluationService baselineService;
    private final IntentTreeRuntimeRepo intentTreeRuntimeRepo;
    private final IntentRoutingEvaluationPreview evaluationPreview;

    public IntentRoutingEvaluationService(IntentEvaluationService baselineService,
                                          IntentTreeRuntimeRepo intentTreeRuntimeRepo,
                                          IntentRoutingEvaluationPreview evaluationPreview) {
        this.baselineService = baselineService;
        this.intentTreeRuntimeRepo = intentTreeRuntimeRepo;
        this.evaluationPreview = evaluationPreview;
    }

    public IntentRoutingEvaluationDataset exportDatasetTemplate(
            Long applicationId, Long applicationVersionId, String datasetVersion) {
        IntentEvaluationDataset baseline = baselineService.exportDatasetTemplate(
                applicationId, applicationVersionId, datasetVersion);
        RuntimeIntentTreeVersionEntity treeVersion = intentTreeRuntimeRepo
                .findVersion(baseline.getIntentTreeId(), baseline.getIntentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "锁定的IntentTreeVersion不存在"));
        IntentRoutingPolicy policy = requireRoutingPolicy(treeVersion);
        return IntentRoutingEvaluationDataset.builder()
                .schemaVersion(IntentRoutingEvaluationDataset.SCHEMA_VERSION)
                .datasetVersion(baseline.getDatasetVersion())
                .applicationId(baseline.getApplicationId())
                .applicationVersionId(baseline.getApplicationVersionId())
                .intentTreeId(baseline.getIntentTreeId())
                .intentTreeVersionId(baseline.getIntentTreeVersionId())
                .embeddingModelInstanceId(baseline.getEmbeddingModelInstanceId())
                .chatModelInstanceId(policy.chatModelInstanceId())
                .promptVersionId(policy.decisionPromptVersionId())
                .leafMetadata(baseline.leafMetadata())
                .samples(List.of())
                .build();
    }

    public IntentRoutingEvaluationReport evaluate(IntentRoutingEvaluationDataset dataset) {
        validate(dataset);
        List<SampleResult> results = new ArrayList<>(dataset.samples().size());
        for (IntentRoutingEvaluationSample sample : dataset.samples()) {
            IntentRoutingPreviewResult result = evaluationPreview.preview(
                    dataset.getApplicationId(), dataset.getApplicationVersionId(), sample.getQuery());
            validatePreviewResult(dataset, result);
            results.add(new SampleResult(sample, result));
        }
        return buildReport(dataset, results);
    }

    /** 在调用模型前校验数据集覆盖率及所有锁定版本契约。 */
    public void validate(IntentRoutingEvaluationDataset dataset) {
        validateDataset(dataset);
        validatePublishedContract(dataset);
    }

    /** 根据外部逐条执行结果生成报告，避免 Admin 为了进度观测重复调用模型。 */
    public IntentRoutingEvaluationReport report(
            IntentRoutingEvaluationDataset dataset, List<IntentRoutingEvaluatedSample> evaluatedSamples) {
        validate(dataset);
        if (evaluatedSamples == null || evaluatedSamples.size() != dataset.samples().size()) {
            throw invalid("评测结果数量与数据集样本数量不一致");
        }
        Map<String, IntentRoutingPreviewResult> resultsBySampleId = new java.util.LinkedHashMap<>();
        for (IntentRoutingEvaluatedSample evaluatedSample : evaluatedSamples) {
            if (evaluatedSample == null || evaluatedSample.sampleId() == null
                    || resultsBySampleId.putIfAbsent(evaluatedSample.sampleId(), evaluatedSample.result()) != null) {
                throw invalid("评测结果包含空值或重复sampleId");
            }
        }
        List<SampleResult> results = new ArrayList<>(dataset.samples().size());
        for (IntentRoutingEvaluationSample sample : dataset.samples()) {
            IntentRoutingPreviewResult result = resultsBySampleId.get(sample.getId());
            validatePreviewResult(dataset, result);
            results.add(new SampleResult(sample, result));
        }
        if (resultsBySampleId.size() != results.size()) {
            throw invalid("评测结果包含数据集之外的sampleId");
        }
        return buildReport(dataset, results);
    }

    private static IntentRoutingEvaluationReport buildReport(
            IntentRoutingEvaluationDataset dataset, List<SampleResult> results) {
        long expectedCandidateCount = results.stream()
                .mapToLong(item -> item.sample().expectedIntentCodes().size())
                .sum();
        long recalledCandidateCount = results.stream()
                .mapToLong(IntentRoutingEvaluationService::recalledCandidateCount)
                .sum();
        long actionCorrectCount = results.stream()
                .filter(item -> item.sample().getExpectedAction() == item.result().getFinalAction())
                .count();
        List<SampleResult> executableExpected = results.stream()
                .filter(item -> EXECUTABLE_ACTIONS.contains(item.sample().getExpectedAction()))
                .toList();
        long selectedCorrectCount = executableExpected.stream()
                .filter(IntentRoutingEvaluationService::isSelectedIntentCorrect)
                .count();
        List<SampleResult> executableActual = results.stream()
                .filter(item -> EXECUTABLE_ACTIONS.contains(item.result().getFinalAction()))
                .toList();
        long executableCorrectCount = executableActual.stream()
                .filter(IntentRoutingEvaluationService::isCorrect)
                .count();
        List<SampleResult> outOfScope = scenario(results, IntentEvaluationScenario.OUT_OF_SCOPE);
        List<SampleResult> multiIntent = scenario(results, IntentEvaluationScenario.MULTI_INTENT);
        List<SampleResult> highRisk = results.stream().filter(item -> item.sample().isHighRisk()).toList();
        long highRiskWrongDirectCount = highRisk.stream()
                .filter(item -> EXECUTABLE_ACTIONS.contains(item.result().getFinalAction()))
                .filter(item -> !isCorrect(item))
                .count();
        long semanticFailureCount = results.stream()
                .filter(item -> item.result().getSemanticReasonCode() == IntentDecisionReason.SEMANTIC_DECISION_FAILED)
                .count();
        IntentRoutingEvaluationMetrics metrics = IntentRoutingEvaluationMetrics.builder()
                .candidateRecallAtK(ratio(recalledCandidateCount, expectedCandidateCount))
                .finalActionAccuracy(ratio(actionCorrectCount, results.size()))
                .selectedIntentAccuracy(ratio(selectedCorrectCount, executableExpected.size()))
                .executablePrecision(ratio(executableCorrectCount, executableActual.size()))
                .outOfScopeDeferRate(actionRate(outOfScope, IntentRoutingAction.DEFER_TO_AGENT))
                .multiIntentSafeRate(actionRate(multiIntent, IntentRoutingAction.DEFER_TO_AGENT))
                .highRiskWrongDirectRate(ratio(highRiskWrongDirectCount, highRisk.size()))
                .semanticFailureRate(ratio(semanticFailureCount, results.size()))
                .p95DurationMillis(p95Duration(results))
                .averageTotalTokens(results.stream()
                        .mapToLong(IntentRoutingEvaluationService::totalTokens)
                        .average().orElse(0D))
                .averageModelCalls(results.stream()
                        .mapToInt(item -> item.result().getModelCalls())
                        .average().orElse(0D))
                .build();
        List<IntentRoutingEvaluationGate> gates = gates(metrics);
        return IntentRoutingEvaluationReport.builder()
                .schemaVersion(IntentRoutingEvaluationDataset.SCHEMA_VERSION)
                .datasetVersion(dataset.getDatasetVersion())
                .generatedAt(Instant.now())
                .applicationId(dataset.getApplicationId())
                .applicationVersionId(dataset.getApplicationVersionId())
                .intentTreeId(dataset.getIntentTreeId())
                .intentTreeVersionId(dataset.getIntentTreeVersionId())
                .embeddingModelInstanceId(dataset.getEmbeddingModelInstanceId())
                .chatModelInstanceId(dataset.getChatModelInstanceId())
                .promptVersionId(dataset.getPromptVersionId())
                .sampleCount(results.size())
                .productionGatePassed(gates.stream().allMatch(IntentRoutingEvaluationGate::passed))
                .metrics(metrics)
                .gates(gates)
                .confusionMatrix(confusionMatrix(results))
                .failures(failures(results))
                .build();
    }

    private static List<IntentRoutingEvaluationGate> gates(IntentRoutingEvaluationMetrics metrics) {
        return List.of(
                minimumGate("CANDIDATE_RECALL_AT_K", metrics.getCandidateRecallAtK(), 0.98D),
                minimumGate("EXECUTABLE_PRECISION", metrics.getExecutablePrecision(), 0.95D),
                minimumGate("OUT_OF_SCOPE_DEFER_RATE", metrics.getOutOfScopeDeferRate(), 0.95D),
                minimumGate("MULTI_INTENT_SAFE_RATE", metrics.getMultiIntentSafeRate(), 1D),
                maximumGate("HIGH_RISK_WRONG_DIRECT_RATE", metrics.getHighRiskWrongDirectRate(), 0D),
                maximumGate("SEMANTIC_FAILURE_RATE", metrics.getSemanticFailureRate(), 0D));
    }

    private static IntentRoutingEvaluationGate minimumGate(String code, double value, double threshold) {
        return new IntentRoutingEvaluationGate(code, value, ">=" + threshold, value >= threshold);
    }

    private static IntentRoutingEvaluationGate maximumGate(String code, double value, double threshold) {
        return new IntentRoutingEvaluationGate(code, value, "<=" + threshold, value <= threshold);
    }

    private static List<IntentRoutingEvaluationFailure> failures(List<SampleResult> results) {
        return results.stream()
                .filter(item -> !isCorrect(item))
                .map(item -> IntentRoutingEvaluationFailure.builder()
                        .sampleId(item.sample().getId())
                        .scenario(item.sample().getScenario())
                        .expectedAction(item.sample().getExpectedAction())
                        .actualAction(item.result().getFinalAction())
                        .expectedIntentCodes(item.sample().expectedIntentCodes())
                        .selectedIntentCode(item.result().getSelectedIntentCode())
                        .candidates(safeList(item.result().getCandidates()))
                        .semanticReasonCode(enumName(item.result().getSemanticReasonCode()))
                        .finalReasonCode(enumName(item.result().getFinalReasonCode()))
                        .build())
                .toList();
    }

    private static List<IntentRoutingEvaluationConfusionEntry> confusionMatrix(List<SampleResult> results) {
        Map<IntentRoutingAction, Map<IntentRoutingAction, Long>> counts = new EnumMap<>(IntentRoutingAction.class);
        for (SampleResult result : results) {
            counts.computeIfAbsent(result.sample().getExpectedAction(), ignored -> new EnumMap<>(IntentRoutingAction.class))
                    .merge(result.result().getFinalAction(), 1L, Long::sum);
        }
        List<IntentRoutingEvaluationConfusionEntry> entries = new ArrayList<>();
        for (IntentRoutingAction expected : allowedActions()) {
            for (IntentRoutingAction actual : allowedActions()) {
                entries.add(new IntentRoutingEvaluationConfusionEntry(expected, actual,
                        counts.getOrDefault(expected, Map.of()).getOrDefault(actual, 0L)));
            }
        }
        return entries;
    }

    private static long recalledCandidateCount(SampleResult item) {
        Set<String> actual = safeList(item.result().getRetrievedCandidates()).stream()
                .map(IntentRoutingPreviewCandidate::intentCode)
                .collect(Collectors.toSet());
        return item.sample().expectedIntentCodes().stream().filter(actual::contains).count();
    }

    private static boolean isCorrect(SampleResult item) {
        if (item.sample().getExpectedAction() != item.result().getFinalAction()) {
            return false;
        }
        if (EXECUTABLE_ACTIONS.contains(item.sample().getExpectedAction())) {
            return isSelectedIntentCorrect(item);
        }
        if (item.sample().getExpectedAction() == IntentRoutingAction.CLARIFICATION) {
            return isClarificationIntentCorrect(item);
        }
        return true;
    }

    private static boolean isSelectedIntentCorrect(SampleResult item) {
        return item.sample().expectedIntentCodes().size() == 1
                && Objects.equals(item.sample().expectedIntentCodes().getFirst(),
                item.result().getSelectedIntentCode());
    }

    private static boolean isClarificationIntentCorrect(SampleResult item) {
        if (item.sample().expectedIntentCodes().size() == 1) {
            return Objects.equals(item.sample().expectedIntentCodes().getFirst(),
                    item.result().getSelectedIntentCode());
        }
        return Set.copyOf(item.sample().expectedIntentCodes())
                .equals(Set.copyOf(safeList(item.result().getAlternativeIntentCodes())));
    }

    private static long p95Duration(List<SampleResult> results) {
        List<Long> durations = results.stream()
                .map(item -> item.result().getDurationMillis())
                .sorted()
                .toList();
        int index = Math.max((int) Math.ceil(durations.size() * 0.95D) - 1, 0);
        return durations.get(index);
    }

    private static long totalTokens(SampleResult item) {
        if (item.result().getTokenUsage() == null || item.result().getTokenUsage().totalTokens() == null) {
            return 0L;
        }
        return item.result().getTokenUsage().totalTokens();
    }

    private static double actionRate(List<SampleResult> results, IntentRoutingAction action) {
        return ratio(results.stream().filter(item -> item.result().getFinalAction() == action).count(), results.size());
    }

    private static List<SampleResult> scenario(List<SampleResult> results, IntentEvaluationScenario scenario) {
        return results.stream().filter(item -> item.sample().getScenario() == scenario).toList();
    }

    private static void validateDataset(IntentRoutingEvaluationDataset dataset) {
        if (dataset == null || dataset.getSchemaVersion() != IntentRoutingEvaluationDataset.SCHEMA_VERSION) {
            throw invalid("V2意图评测数据集协议版本不受支持");
        }
        requireText(dataset.getDatasetVersion(), "datasetVersion不能为空");
        requireText(dataset.getLabelingOwner(), "labelingOwner不能为空");
        requireText(dataset.getSourceDescription(), "sourceDescription不能为空");
        if (dataset.getLabeledAt() == null) {
            throw invalid("labeledAt不能为空");
        }
        if (dataset.getApplicationId() == null || dataset.getApplicationVersionId() == null
                || dataset.getIntentTreeId() == null || dataset.getIntentTreeVersionId() == null
                || dataset.getEmbeddingModelInstanceId() == null || dataset.getChatModelInstanceId() == null
                || dataset.getPromptVersionId() == null) {
            throw invalid("V2意图评测数据集缺少锁定版本信息");
        }
        validateSamples(dataset.samples());
    }

    private static void validateSamples(List<IntentRoutingEvaluationSample> samples) {
        if (samples.size() < MINIMUM_PRODUCTION_SAMPLE_COUNT) {
            throw invalid("V2生产门禁样本不得少于300条");
        }
        Set<String> ids = new HashSet<>();
        Set<IntentEvaluationScenario> scenarios = EnumSet.noneOf(IntentEvaluationScenario.class);
        Set<IntentRoutingAction> actions = EnumSet.noneOf(IntentRoutingAction.class);
        boolean highRiskExecutableCovered = false;
        for (IntentRoutingEvaluationSample sample : samples) {
            if (sample == null || !ids.add(requireText(sample.getId(), "样本id不能为空"))) {
                throw invalid("样本id重复");
            }
            requireText(sample.getQuery(), "样本query不能为空");
            if (sample.getScenario() == null || sample.getExpectedAction() == null) {
                throw invalid("样本缺少scenario或expectedAction");
            }
            if (!allowedActions().contains(sample.getExpectedAction())) {
                throw invalid("样本expectedAction不允许使用SKIPPED");
            }
            validateExpectedCodes(sample);
            scenarios.add(sample.getScenario());
            actions.add(sample.getExpectedAction());
            highRiskExecutableCovered = highRiskExecutableCovered
                    || sample.isHighRisk() && EXECUTABLE_ACTIONS.contains(sample.getExpectedAction());
        }
        if (!scenarios.containsAll(EnumSet.allOf(IntentEvaluationScenario.class))) {
            throw invalid("V2生产门禁数据集未覆盖全部规定场景");
        }
        if (!actions.containsAll(EnumSet.of(
                IntentRoutingAction.EXECUTE_AGENT,
                IntentRoutingAction.EXECUTE_WORKFLOW,
                IntentRoutingAction.FIXED_RESPONSE,
                IntentRoutingAction.CLARIFICATION,
                IntentRoutingAction.DEFER_TO_AGENT))) {
            throw invalid("V2生产门禁数据集未覆盖全部最终动作");
        }
        if (!highRiskExecutableCovered) {
            throw invalid("V2生产门禁数据集缺少高风险能力样本");
        }
    }

    private void validatePublishedContract(IntentRoutingEvaluationDataset dataset) {
        IntentEvaluationDataset lockedDataset = baselineService.exportDatasetTemplate(
                dataset.getApplicationId(), dataset.getApplicationVersionId(), dataset.getDatasetVersion());
        if (!Objects.equals(dataset.getApplicationId(), lockedDataset.getApplicationId())
                || !Objects.equals(dataset.getApplicationVersionId(), lockedDataset.getApplicationVersionId())
                || !Objects.equals(dataset.getIntentTreeId(), lockedDataset.getIntentTreeId())
                || !Objects.equals(dataset.getIntentTreeVersionId(), lockedDataset.getIntentTreeVersionId())
                || !Objects.equals(dataset.getEmbeddingModelInstanceId(),
                lockedDataset.getEmbeddingModelInstanceId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "V2意图评测数据集与ApplicationVersion锁定版本不一致");
        }
        Set<String> lockedLeafCodes = leafCodes(lockedDataset.leafMetadata(), HttpStatusEnum.CONFLICT,
                "锁定的IntentTreeVersion叶子意图快照无效");
        Set<String> datasetLeafCodes = leafCodes(dataset.leafMetadata(), HttpStatusEnum.BAD_REQUEST,
                "V2意图评测数据集的叶子意图快照无效");
        if (!datasetLeafCodes.equals(lockedLeafCodes)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "V2意图评测数据集的叶子意图快照与锁定版本不一致");
        }
        for (IntentRoutingEvaluationSample sample : dataset.samples()) {
            if (!lockedLeafCodes.containsAll(sample.expectedIntentCodes())) {
                throw invalid("样本包含当前发布意图树不存在的expectedIntentCode");
            }
        }
        RuntimeIntentTreeVersionEntity treeVersion = intentTreeRuntimeRepo
                .findVersion(lockedDataset.getIntentTreeId(), lockedDataset.getIntentTreeVersionId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT,
                        "锁定的IntentTreeVersion不存在"));
        IntentRoutingPolicy policy = requireRoutingPolicy(treeVersion);
        if (!Objects.equals(dataset.getChatModelInstanceId(), policy.chatModelInstanceId())
                || !Objects.equals(dataset.getPromptVersionId(), policy.decisionPromptVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "V2意图评测数据集的模型或Prompt与锁定意图树版本不一致");
        }
    }

    private static Set<String> leafCodes(
            List<IntentEvaluationLeafMetadata> leaves, HttpStatusEnum status, String message) {
        Set<String> codes = new HashSet<>();
        for (IntentEvaluationLeafMetadata leaf : leaves) {
            if (leaf == null || leaf.getIntentCode() == null || leaf.getIntentCode().isBlank()
                    || !codes.add(leaf.getIntentCode())) {
                throw new BusinessException(status, message);
            }
        }
        if (codes.isEmpty()) {
            throw new BusinessException(status, message);
        }
        return Set.copyOf(codes);
    }

    private static void validateExpectedCodes(IntentRoutingEvaluationSample sample) {
        List<String> codes = sample.expectedIntentCodes();
        if (codes.stream().anyMatch(code -> code == null || code.isBlank())
                || codes.size() != new HashSet<>(codes).size()) {
            throw invalid("样本expectedIntentCodes包含空值或重复值");
        }
        if (EXECUTABLE_ACTIONS.contains(sample.getExpectedAction()) && codes.size() != 1) {
            throw invalid("可执行动作样本必须填写一个expectedIntentCode");
        }
        if (sample.getExpectedAction() == IntentRoutingAction.CLARIFICATION && codes.isEmpty()) {
            throw invalid("澄清样本至少填写一个expectedIntentCode");
        }
    }

    private static void validatePreviewResult(
            IntentRoutingEvaluationDataset dataset, IntentRoutingPreviewResult result) {
        if (result == null || result.getFinalAction() == null || !allowedActions().contains(result.getFinalAction())
                || result.getModelCalls() < 0 || result.getRetryCount() < 0 || result.getDurationMillis() < 0L) {
            throw new BusinessException(HttpStatusEnum.SERVICE_UNAVAILABLE, "V2意图评测响应协议不完整");
        }
        if (!Objects.equals(dataset.getChatModelInstanceId(), result.getModelInstanceId())
                || !Objects.equals(dataset.getPromptVersionId(), result.getPromptVersionId())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "评测响应的模型或Prompt与数据集锁定版本不一致");
        }
    }

    private static IntentRoutingPolicy requireRoutingPolicy(RuntimeIntentTreeVersionEntity version) {
        IntentTreeDefinition definition = version.getDefinitionSnapshot();
        if (definition == null || definition.schemaVersion() != INTENT_TREE_SCHEMA_VERSION) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "锁定的IntentTreeVersion协议不受支持");
        }
        if (definition.routingPolicy() == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "锁定的IntentTreeVersion缺少路由策略");
        }
        return definition.routingPolicy();
    }

    private static Set<IntentRoutingAction> allowedActions() {
        return EnumSet.of(
                IntentRoutingAction.EXECUTE_AGENT,
                IntentRoutingAction.EXECUTE_WORKFLOW,
                IntentRoutingAction.FIXED_RESPONSE,
                IntentRoutingAction.CLARIFICATION,
                IntentRoutingAction.DEFER_TO_AGENT);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.trim();
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0L ? 0D : (double) numerator / denominator;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.BAD_REQUEST, message);
    }

    /**
     * @param sample 人工期望
     * @param result 生产版本决策结果
     */
    private record SampleResult(IntentRoutingEvaluationSample sample, IntentRoutingPreviewResult result) {
    }
}
